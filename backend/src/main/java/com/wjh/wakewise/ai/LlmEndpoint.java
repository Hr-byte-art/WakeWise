package com.wjh.wakewise.ai;

/**
 * LLM 端点三元组（BYOK）：服务端默认（环境变量）或用户级（设置页）解析后的值对象。
 * of() 做规范化（trim、base-url 去尾斜杠——拼接 "/chat/completions" 时避免双斜杠），
 * complete() 三项均非空才可用，不完整的一律视为未配置走模板兜底。
 */
public record LlmEndpoint(String baseUrl, String apiKey, String model) {

    public static LlmEndpoint of(String baseUrl, String apiKey, String model) {
        String url = baseUrl == null ? "" : baseUrl.strip();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return new LlmEndpoint(url,
                apiKey == null ? "" : apiKey.strip(),
                model == null ? "" : model.strip());
    }

    public boolean complete() {
        return !baseUrl.isBlank() && !apiKey.isBlank() && !model.isBlank();
    }
}
