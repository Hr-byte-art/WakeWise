package com.wjh.wakewise.domain.user;

import com.wjh.wakewise.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 用户级外部服务凭据（V5，BYOK）：每用户每服务一行，行缺省即继承服务端默认。
 * disabled=true 为用户显式禁用，压过服务端默认配置；
 * secretEnc 是 api_key 密文（AES-256-GCM），任何接口不回显明文；
 * llmBaseUrl/llmModel 非机密，明文存储供设置页状态回显。
 * 并发写入由 uk_user_credential(user_id, service) 唯一键兜底（镜像 V3 device_event 的幂等思路）。
 */
@Entity
@Table(name = "user_credential")
public class UserCredential extends BaseEntity {

    public enum Service { AMAP, LLM }

    @Id
    @Column(length = 26)
    private String id;

    @Column(name = "user_id", nullable = false, length = 26)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Service service;

    @Column(nullable = false)
    private boolean disabled;

    @Column(name = "llm_base_url", length = 255)
    private String llmBaseUrl;

    @Column(name = "llm_model", length = 100)
    private String llmModel;

    @Column(name = "secret_enc", length = 512)
    private String secretEnc;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public Service getService() { return service; }
    public void setService(Service service) { this.service = service; }
    public boolean isDisabled() { return disabled; }
    public void setDisabled(boolean disabled) { this.disabled = disabled; }
    public String getLlmBaseUrl() { return llmBaseUrl; }
    public void setLlmBaseUrl(String llmBaseUrl) { this.llmBaseUrl = llmBaseUrl; }
    public String getLlmModel() { return llmModel; }
    public void setLlmModel(String llmModel) { this.llmModel = llmModel; }
    public String getSecretEnc() { return secretEnc; }
    public void setSecretEnc(String secretEnc) { this.secretEnc = secretEnc; }
}
