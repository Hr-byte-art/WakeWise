package com.wjh.wakewise.common;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class AuthContext {

    private AuthContext() {
    }

    /** JWT subject 即用户 ID，由 JwtAuthFilter 写入 SecurityContext。 */
    public static String currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())
                || !(authentication.getPrincipal() instanceof String userId)) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED, "未认证");
        }
        return userId;
    }
}
