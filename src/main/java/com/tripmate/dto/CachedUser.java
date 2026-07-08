package com.tripmate.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * Redis 缓存的用户信息 DTO。
 *
 * <p>仅包含认证和授权所需的最少字段，不包含密码等敏感信息。
 * 实现 Serializable 以支持 Redis JSON 序列化。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CachedUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户 ID */
    private Long id;

    /** 用户名 */
    private String username;

    /** Spring Security 权限列表，如 ["ROLE_USER"] 或 ["ROLE_ADMIN"] */
    private List<String> authorities;
}
