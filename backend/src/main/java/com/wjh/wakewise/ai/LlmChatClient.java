package com.wjh.wakewise.ai;

import java.util.Optional;

/**
 * LLM 对话端口（OpenAI 兼容 chat/completions 的最小抽象）。
 * 约定：任何失败（未配置/超时/报错）都返回 Optional.empty()，不抛异常——
 * 增强链路只降级不扰动主流程。接口即测试替身缝：单测手写小实现即可，不引 Mockito。
 */
public interface LlmChatClient {

    /** 配置（base-url/api-key/model）齐全才视为已启用；未启用时调用方直接走模板。 */
    boolean configured();

    /** 单轮对话；返回首条回复文本，失败返回 empty。 */
    Optional<String> chat(String systemPrompt, String userPrompt);
}
