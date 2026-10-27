package com.wjh.wakewise.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** LlmEndpoint 纯函数断言：规范化与完整性判定。 */
class LlmEndpointTest {

    @Test
    void ofTrimsAndStripsTrailingSlash() {
        // base-url 常见手滑：尾斜杠/空格——拼接 /chat/completions 前先去掉
        LlmEndpoint ep = LlmEndpoint.of(" https://api.deepseek.com/v1/ ", " sk-abc ", " deepseek-chat ");
        assertThat(ep.baseUrl()).isEqualTo("https://api.deepseek.com/v1");
        assertThat(ep.apiKey()).isEqualTo("sk-abc");
        assertThat(ep.model()).isEqualTo("deepseek-chat");
        assertThat(ep.complete()).isTrue();
    }

    @Test
    void ofHandlesMultipleTrailingSlashesAndNulls() {
        assertThat(LlmEndpoint.of("https://x.com/v1//", "k", "m").baseUrl()).isEqualTo("https://x.com/v1");
        // null 与空白一律归一为空串（后续 complete() 判 false）
        LlmEndpoint empty = LlmEndpoint.of(null, "  ", null);
        assertThat(empty.baseUrl()).isEmpty();
        assertThat(empty.apiKey()).isEmpty();
        assertThat(empty.model()).isEmpty();
    }

    @Test
    void completeRequiresAllThreeFields() {
        assertThat(LlmEndpoint.of("https://x.com/v1", "k", "m").complete()).isTrue();
        // 缺任一项即不完整 → 调用方按未配置处理（模板兜底），半配置不允许部分生效
        assertThat(LlmEndpoint.of("", "k", "m").complete()).isFalse();
        assertThat(LlmEndpoint.of("https://x.com/v1", "", "m").complete()).isFalse();
        assertThat(LlmEndpoint.of("https://x.com/v1", "k", "").complete()).isFalse();
    }
}
