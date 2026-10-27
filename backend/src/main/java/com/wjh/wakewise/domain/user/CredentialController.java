package com.wjh.wakewise.domain.user;

import com.wjh.wakewise.common.ApiResponse;
import com.wjh.wakewise.common.AuthContext;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 外部服务凭据（BYOK）设置接口。解析链：用户显式禁用 ＞ 用户级凭据 ＞ 服务端默认 ＞ 关闭。
 * GET 只显生效来源不回显 api_key 明文；PUT 写入（api_key 加密落库）/禁用/清除恢复继承。
 */
@RestController
@RequestMapping("/api/v1/me/external-services")
public class CredentialController {

    private final CredentialService credentialService;

    public CredentialController(CredentialService credentialService) {
        this.credentialService = credentialService;
    }

    /** 两服务（AMAP/LLM）当前生效来源。 */
    @GetMapping
    public ApiResponse<List<CredentialDtos.ServiceStatusView>> status() {
        return ApiResponse.ok(credentialService.status(AuthContext.currentUserId()));
    }

    /**
     * 更新某服务的用户级配置：body 带凭据即写入（api_key 加密落库）；
     * disabled=true 显式禁用（压过服务端默认）；凭据字段全空/缺省 = 清除，恢复继承。
     */
    @PutMapping("/{service}")
    public ApiResponse<CredentialDtos.ServiceStatusView> update(
            @PathVariable String service,
            @Valid @RequestBody CredentialDtos.UpdateCredentialRequest request) {
        return ApiResponse.ok(credentialService.update(AuthContext.currentUserId(), service, request));
    }
}
