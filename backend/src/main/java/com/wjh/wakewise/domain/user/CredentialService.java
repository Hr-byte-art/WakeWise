package com.wjh.wakewise.domain.user;

import com.wjh.wakewise.ai.LlmEndpoint;
import com.wjh.wakewise.common.BusinessException;
import com.wjh.wakewise.common.ErrorCode;
import com.wjh.wakewise.common.Ids;
import com.wjh.wakewise.common.SecretCipher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户级外部服务凭据（BYOK，V5）解析。
 * 解析链：用户显式禁用 ＞ 用户级凭据 ＞ 服务端默认（环境变量 AMAP_KEY / LLM_*）＞ 关闭。
 * 红线：amapFor/llmFor 永不抛——读库/解密任何失败只记日志并按"无用户级配置"处理，
 * 回落服务端默认，绝不影响闹钟主链路（"AI 可以失败，闹钟不能"）。
 * 读取侧：DecisionService.compute / MorningContextService 每次现调（镜像 priorsFor 模式）。
 */
@Service
public class CredentialService {

    private static final Logger log = LoggerFactory.getLogger(CredentialService.class);

    /** 生效来源，设置页按此回显状态行。 */
    public enum Source { DISABLED, USER, SERVER, OFF }

    /** 高德解析结果：available() 为 true 时 key() 可直接用于 AmapClient。 */
    public record AmapAccess(Source source, String key) {
        public boolean available() {
            return (source == Source.USER || source == Source.SERVER) && key != null && !key.isBlank();
        }
    }

    /** LLM 解析结果：available() 为 true 时 endpoint() 完整可直接调用。 */
    public record LlmAccess(Source source, LlmEndpoint endpoint) {
        public boolean available() {
            return (source == Source.USER || source == Source.SERVER) && endpoint != null && endpoint.complete();
        }
    }

    private final UserCredentialRepository repository;
    private final SecretCipher cipher;
    private final String serverAmapKey;
    private final LlmEndpoint serverLlm;

    public CredentialService(UserCredentialRepository repository,
                             SecretCipher cipher,
                             @Value("${wakewise.amap.key:}") String serverAmapKey,
                             @Value("${wakewise.llm.base-url:}") String serverLlmBaseUrl,
                             @Value("${wakewise.llm.api-key:}") String serverLlmApiKey,
                             @Value("${wakewise.llm.model:}") String serverLlmModel) {
        this.repository = repository;
        this.cipher = cipher;
        this.serverAmapKey = serverAmapKey == null ? "" : serverAmapKey.strip();
        this.serverLlm = LlmEndpoint.of(serverLlmBaseUrl, serverLlmApiKey, serverLlmModel);
    }

    /** 高德生效凭据；永不抛。 */
    @Transactional(readOnly = true)
    public AmapAccess amapFor(String userId) {
        try {
            return repository.findByUserIdAndService(userId, UserCredential.Service.AMAP)
                    .map(row -> resolveAmap(row.isDisabled(),
                            decryptQuietly(row.getSecretEnc(), userId, "AMAP"), serverAmapKey))
                    .orElse(resolveAmap(false, null, serverAmapKey));
        } catch (Exception e) {
            log.warn("AMAP credential resolve failed, falling back to server default: {}", e.getMessage());
            return resolveAmap(false, null, serverAmapKey);
        }
    }

    /** LLM 生效凭据；永不抛。 */
    @Transactional(readOnly = true)
    public LlmAccess llmFor(String userId) {
        try {
            return repository.findByUserIdAndService(userId, UserCredential.Service.LLM)
                    .map(row -> resolveLlm(row.isDisabled(),
                            LlmEndpoint.of(row.getLlmBaseUrl(),
                                    decryptQuietly(row.getSecretEnc(), userId, "LLM"), row.getLlmModel()),
                            serverLlm))
                    .orElse(resolveLlm(false, null, serverLlm));
        } catch (Exception e) {
            log.warn("LLM credential resolve failed, falling back to server default: {}", e.getMessage());
            return resolveLlm(false, null, serverLlm);
        }
    }

    // ------------------------------------------------------------------ 设置页读写

    /** 两服务当前生效来源（api_key 不回显；LLM 用户级生效时附带 baseUrl/model）。 */
    @Transactional(readOnly = true)
    public List<CredentialDtos.ServiceStatusView> status(String userId) {
        return List.of(statusOf(userId, UserCredential.Service.AMAP),
                statusOf(userId, UserCredential.Service.LLM));
    }

    /** 写入/禁用/清除用户级凭据，语义矩阵见 CredentialDtos.UpdateCredentialRequest。 */
    @Transactional
    public CredentialDtos.ServiceStatusView update(String userId, String service,
                                                   CredentialDtos.UpdateCredentialRequest request) {
        UserCredential.Service parsed = parseService(service);
        boolean disable = request != null && Boolean.TRUE.equals(request.disabled());
        String apiKey = normalize(request == null ? null : request.apiKey());
        String baseUrl = normalize(request == null ? null : request.baseUrl());
        String model = normalize(request == null ? null : request.model());

        if (disable && (apiKey != null || baseUrl != null || model != null)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "禁用与凭据配置不能同时提交");
        }
        if (!disable && apiKey == null && baseUrl == null && model == null) {
            // 全空/缺省：清除用户级配置，恢复继承服务端默认
            repository.findByUserIdAndService(userId, parsed).ifPresent(repository::delete);
            return statusOf(userId, parsed);
        }
        if (disable) {
            upsert(userId, parsed, true, null, null, null);
            return statusOf(userId, parsed);
        }
        if (parsed == UserCredential.Service.AMAP) {
            if (baseUrl != null || model != null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "AMAP 仅接受 apiKey 字段");
            }
            upsert(userId, parsed, false, cipher.encrypt(apiKey), null, null);
            return statusOf(userId, parsed);
        }
        // LLM 三项须同时填写（all-or-nothing，半配不允许部分生效）
        if (apiKey == null || baseUrl == null || model == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "LLM 需同时填写 base-url/api-key/model");
        }
        upsert(userId, parsed, false, cipher.encrypt(apiKey), baseUrl, model);
        return statusOf(userId, parsed);
    }

    private static UserCredential.Service parseService(String service) {
        try {
            return UserCredential.Service.valueOf(service == null ? "" : service.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "service 必须为 AMAP 或 LLM");
        }
    }

    private void upsert(String userId, UserCredential.Service service, boolean disabled,
                        String secretEnc, String llmBaseUrl, String llmModel) {
        UserCredential row = repository.findByUserIdAndService(userId, service)
                .orElseGet(() -> {
                    UserCredential created = new UserCredential();
                    created.setId(Ids.newId());
                    created.setUserId(userId);
                    created.setService(service);
                    return created;
                });
        row.setDisabled(disabled);
        row.setSecretEnc(secretEnc);
        row.setLlmBaseUrl(llmBaseUrl);
        row.setLlmModel(llmModel);
        repository.save(row);
    }

    private CredentialDtos.ServiceStatusView statusOf(String userId, UserCredential.Service service) {
        if (service == UserCredential.Service.AMAP) {
            return CredentialDtos.ServiceStatusView.of(service, amapFor(userId).source(), null, null);
        }
        LlmAccess llm = llmFor(userId);
        // baseUrl/model 非机密可回显，但仅在用户级生效时给出（服务端配置不外泄细节）
        boolean userActive = llm.source() == Source.USER && llm.endpoint() != null;
        return CredentialDtos.ServiceStatusView.of(service, llm.source(),
                userActive ? llm.endpoint().baseUrl() : null,
                userActive ? llm.endpoint().model() : null);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    // ------------------------------------------------------------------ 解析链（纯函数，可直测）

    /** 链：禁用 ＞ 用户级（值完整）＞ 服务端默认（值完整）＞ OFF；用户级半值视同无。 */
    static AmapAccess resolveAmap(boolean disabled, String userKey, String serverKey) {
        if (disabled) {
            return new AmapAccess(Source.DISABLED, null);
        }
        if (userKey != null && !userKey.isBlank()) {
            return new AmapAccess(Source.USER, userKey);
        }
        if (serverKey != null && !serverKey.isBlank()) {
            return new AmapAccess(Source.SERVER, serverKey);
        }
        return new AmapAccess(Source.OFF, null);
    }

    static LlmAccess resolveLlm(boolean disabled, LlmEndpoint userEndpoint, LlmEndpoint serverEndpoint) {
        if (disabled) {
            return new LlmAccess(Source.DISABLED, null);
        }
        if (userEndpoint != null && userEndpoint.complete()) {
            return new LlmAccess(Source.USER, userEndpoint);
        }
        if (serverEndpoint != null && serverEndpoint.complete()) {
            return new LlmAccess(Source.SERVER, serverEndpoint);
        }
        return new LlmAccess(Source.OFF, null);
    }

    /** 解密失败（密钥轮换/密文损坏）按无用户级配置处理，用户重存即恢复。 */
    private String decryptQuietly(String encoded, String userId, String service) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            return cipher.decrypt(encoded);
        } catch (Exception e) {
            log.warn("User credential decrypt failed (user={}, service={}): {}", userId, service, e.getMessage());
            return null;
        }
    }
}
