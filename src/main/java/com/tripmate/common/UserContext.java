package com.tripmate.common;

import com.tripmate.dto.CachedUser;

/**
 * 用户上下文工具类，基于 ThreadLocal 保存当前请求的用户信息。
 *
 * <p>由 {@code JwtAuthenticationFilter} 在每次请求时根据 JWT Token
 * 解析并设置当前用户，请求结束后在 finally 块中自动清理，防止内存泄漏。</p>
 *
 * <p>使用方式：在任意 Service 或 Controller 中调用静态方法即可获取当前用户信息。
 * 未认证请求返回 null。</p>
 *
 * <pre>{@code
 * Long userId = UserContext.getUserId();
 * String username = UserContext.getUsername();
 * }</pre>
 */
public class UserContext {

    /** ThreadLocal 持有当前请求的用户，线程隔离保证并发安全 */
    private static final ThreadLocal<CachedUser> USER_HOLDER = new ThreadLocal<>();

    /** 设置当前线程的用户信息 */
    public static void set(CachedUser user) {
        USER_HOLDER.set(user);
    }

    /** 获取当前线程的用户信息，未认证时返回 null */
    public static CachedUser get() {
        return USER_HOLDER.get();
    }

    /** 便捷获取当前用户 ID，未认证时返回 null */
    public static Long getUserId() {
        CachedUser user = get();
        return user != null ? user.getId() : null;
    }

    /** 便捷获取当前用户名，未认证时返回 null */
    public static String getUsername() {
        CachedUser user = get();
        return user != null ? user.getUsername() : null;
    }

    /** 请求结束后调用，清理 ThreadLocal，防止线程池复用时数据串扰 */
    public static void clear() {
        USER_HOLDER.remove();
    }
}
