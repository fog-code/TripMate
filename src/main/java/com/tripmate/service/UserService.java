package com.tripmate.service;

import com.tripmate.dto.CachedUser;
import com.tripmate.dto.LoginRequest;
import com.tripmate.dto.RegisterRequest;

import java.util.Map;

/**
 * 用户服务接口。
 *
 * <p>提供用户注册、登录和缓存查询功能。</p>
 */
public interface UserService {

    /** 用户注册，密码 BCrypt 加密后存储，返回 userId + username + JWT token */
    Map<String, Object> register(RegisterRequest request);

    /** 用户登录，校验密码后返回 JWT token，同时将用户信息写入 Redis 缓存 */
    Map<String, Object> login(LoginRequest request);

    /**
     * 根据用户 ID 获取缓存用户信息。
     * 优先从 Redis 读取（key: user:{id}），未命中时查 MySQL 并回填缓存。
     */
    CachedUser getUserById(Long userId);
}
