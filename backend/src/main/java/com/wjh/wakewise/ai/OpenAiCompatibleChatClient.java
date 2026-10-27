package com.wjh.wakewise.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * OpenAI 兼容 chat/completions 客户端：base-url/api-key/model 全部用户可配，
 * 任何 OpenAI 兼容服务（DeepSeek / OpenAI / 通义 / Kimi 等）都能接。
 * 与 OpenMeteoClient 同款降级哲学：所有失败返回 Optional.empty() 并记日志，
 * 由上层回落模板——"AI 可以失败，闹钟不能"。
 */
@Component
public class OpenAiCompatibleChatClient implements LlmChatClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleChatClient.class);

    private final RestClient restClient;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAiCompatibleChatClient(
            @Value("${wakewise.llm.base-url:}") String baseUrl,
            @Value("${wakewise.llm.api-key:}") String apiKey,
            @Value("${wakewise.llm.model:}") String model,
            @Value("${wakewise.llm.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${wakewise.llm.read-timeout-ms:5000}") int readTimeoutMs) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null ? "" : model.trim();
        // 紧超时：LLM 在决策事务内被同步调用（与天气链路同款风险姿态），必须快速失败
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public boolean configured() {
        return !baseUrl.isEmpty() && !apiKey.isEmpty() && !model.isEmpty();
    }

    @Override
    public Optional<String> chat(String systemPrompt, String userPrompt) {
        if (!configured()) {
            return Optional.empty();
        }
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)),
                    "temperature", 0.3,
                    "max_tokens", 400,
                    "stream", false);
            String response = restClient.post()
                    .uri(baseUrl + "/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode content = objectMapper.readTree(response)
                    .path("choices").path(0).path("message").path("content");
            return content.isMissingNode() ? Optional.empty() : Optional.ofNullable(content.asText());
        } catch (Exception e) {
            // 增强链路：失败只记日志，由上层回落模板
            log.warn("LLM chat completion failed: model={}, {}", model, e.getMessage());
            return Optional.empty();
        }
    }
}
