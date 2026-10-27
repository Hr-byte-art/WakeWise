package com.wjh.wakewise.domain.user;

import com.wjh.wakewise.ai.LlmEndpoint;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 解析链纯函数全分支断言：禁用 ＞ 用户级 ＞ 服务端默认 ＞ OFF，半值回落。 */
class CredentialServiceTest {

    private static final LlmEndpoint FULL_USER = LlmEndpoint.of("https://user.example/v1", "sk-user", "user-model");
    private static final LlmEndpoint FULL_SERVER = LlmEndpoint.of("https://server.example/v1", "sk-server", "server-model");

    // ------------------------------------------------------------------ 高德

    @Test
    void amapDisabledBeatsUserAndServer() {
        // 用户显式禁用压过一切——即使自带 key 和服务端默认都在
        CredentialService.AmapAccess access = CredentialService.resolveAmap(true, "user-key", "server-key");
        assertThat(access.source()).isEqualTo(CredentialService.Source.DISABLED);
        assertThat(access.key()).isNull();
        assertThat(access.available()).isFalse();
    }

    @Test
    void amapUserKeyWinsOverServerDefault() {
        CredentialService.AmapAccess access = CredentialService.resolveAmap(false, "user-key", "server-key");
        assertThat(access.source()).isEqualTo(CredentialService.Source.USER);
        assertThat(access.key()).isEqualTo("user-key");
        assertThat(access.available()).isTrue();
    }

    @Test
    void amapFallsBackToServerWhenNoUserKey() {
        // 无行/解密失败都表现为 userKey=null → 服务端默认
        CredentialService.AmapAccess access = CredentialService.resolveAmap(false, null, "server-key");
        assertThat(access.source()).isEqualTo(CredentialService.Source.SERVER);
        assertThat(access.key()).isEqualTo("server-key");
        assertThat(access.available()).isTrue();
        // 空白串（半行）同样回落
        assertThat(CredentialService.resolveAmap(false, "  ", "").source()).isEqualTo(CredentialService.Source.OFF);
    }

    @Test
    void amapOffWhenNothingConfigured() {
        CredentialService.AmapAccess access = CredentialService.resolveAmap(false, null, null);
        assertThat(access.source()).isEqualTo(CredentialService.Source.OFF);
        assertThat(access.available()).isFalse();
    }

    // ------------------------------------------------------------------ LLM

    @Test
    void llmDisabledBeatsEverything() {
        CredentialService.LlmAccess access = CredentialService.resolveLlm(true, FULL_USER, FULL_SERVER);
        assertThat(access.source()).isEqualTo(CredentialService.Source.DISABLED);
        assertThat(access.endpoint()).isNull();
        assertThat(access.available()).isFalse();
    }

    @Test
    void llmUserEndpointWinsOverServer() {
        CredentialService.LlmAccess access = CredentialService.resolveLlm(false, FULL_USER, FULL_SERVER);
        assertThat(access.source()).isEqualTo(CredentialService.Source.USER);
        assertThat(access.endpoint()).isEqualTo(FULL_USER);
        assertThat(access.available()).isTrue();
    }

    @Test
    void llmHalfFilledUserEndpointFallsBackToServer() {
        // 用户行只存了部分（历史半行/解密失败）→ 视同无用户配置，不允许部分生效
        LlmEndpoint half = LlmEndpoint.of("https://user.example/v1", "", "user-model");
        CredentialService.LlmAccess access = CredentialService.resolveLlm(false, half, FULL_SERVER);
        assertThat(access.source()).isEqualTo(CredentialService.Source.SERVER);
        assertThat(access.endpoint()).isEqualTo(FULL_SERVER);
    }

    @Test
    void llmServerWhenNoUserRow() {
        assertThat(CredentialService.resolveLlm(false, null, FULL_SERVER).source())
                .isEqualTo(CredentialService.Source.SERVER);
    }

    @Test
    void llmOffWhenNeitherComplete() {
        LlmEndpoint halfServer = LlmEndpoint.of("https://server.example/v1", "sk-server", "");
        assertThat(CredentialService.resolveLlm(false, halfServer, halfServer).source())
                .isEqualTo(CredentialService.Source.OFF);
        assertThat(CredentialService.resolveLlm(false, null, null).source())
                .isEqualTo(CredentialService.Source.OFF);
    }
}
