package com.wjh.wakewise.domain.user;

import jakarta.validation.constraints.Size;

/** 设置页（BYOK 外部服务凭据）接口 DTO。 */
public final class CredentialDtos {

    private CredentialDtos() {
    }

    /**
     * PUT 请求体：写入用户级凭据或显式禁用。
     * 凭据字段全空/缺省 = 清除该服务用户级配置（恢复继承服务端默认）；
     * disabled=true 与凭据字段互斥；LLM 三项（baseUrl/apiKey/model）须同时填写。
     * @Size 上限对齐 user_credential 列宽（api_key 密文膨胀 4/3 倍后仍在 secret_enc 512 内）。
     */
    public record UpdateCredentialRequest(
            @Size(max = 200, message = "apiKey 最长 200 字符") String apiKey,
            @Size(max = 255, message = "baseUrl 最长 255 字符") String baseUrl,
            @Size(max = 100, message = "model 最长 100 字符") String model,
            Boolean disabled) {
    }

    /** 状态回显：只显生效来源与中文标签，api_key 一律不回显；LLM 用户级生效时附带 baseUrl/model（非机密）。 */
    public record ServiceStatusView(String service, String source, String sourceLabel,
                                    String baseUrl, String model) {

        static ServiceStatusView of(UserCredential.Service service, CredentialService.Source source,
                                    String baseUrl, String model) {
            String label = switch (source) {
                case USER -> "你的配置生效中";
                case SERVER -> "服务端默认";
                case DISABLED -> "已禁用";
                case OFF -> "未启用";
            };
            return new ServiceStatusView(service.name(), source.name(), label, baseUrl, model);
        }
    }
}
