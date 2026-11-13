package com.xinyu.security;

import com.xinyu.common.BusinessException;
import com.xinyu.common.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全上下文工具类
 */
public final class SecurityUtil {

    private SecurityUtil() {
    }

    /** 获取当前登录用户 ID */
    public static Long getCurrentUserId() {
        LoginUser loginUser = getLoginUser();
        return loginUser.getId();
    }

    public static LoginUser getLoginUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return loginUser;
    }

    public static boolean isAdmin() {
        return getLoginUser().isAdmin();
    }
}
