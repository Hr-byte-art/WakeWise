package com.wjh.wakewise.ai;

import java.util.Optional;

/**
 * LLM 对话端口（OpenAI 兼容 chat/completions 的最小抽象）。
 * 端点三元组由调用方解析（用户级 BYOK ＞ 服务端默认环境变量，见 CredentialService）
 * 后按调用传入——客户端自身不持有配置。约定：任何失败（端点不完整/超时/报错）
 * 都返回 Optional.empty()，不抛异常——增强链路只降级不扰动主流程。
 * 接口即测试替身缝：单测手写小实现即可，不引 Mockito。
 */
public interface LlmChatClient {

    /** 单轮对话；endpoint 为本次生效端点（null/不完整返回 empty），返回首条回复文本。 */
    Optional<String> chat(String systemPrompt, String userPrompt, LlmEndpoint endpoint);
}
