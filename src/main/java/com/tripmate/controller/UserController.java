package com.tripmate.controller;

import com.tripmate.common.Result;
import com.tripmate.dto.LoginRequest;
import com.tripmate.dto.RegisterRequest;
import com.tripmate.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户认证控制器。
 *
 * <p>提供用户注册和登录功能：
 * <ul>
 *   <li>注册时校验用户名/邮箱唯一性，密码 BCrypt 加密后存储</li>
 *   <li>登录成功后返回 JWT Token（7 天有效期），同时将用户信息缓存到 Redis</li>
 * </ul></p>
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** 用户注册：用户名+密码+邮箱，密码至少 8 位，用户名和邮箱不可重复 */
    @PostMapping("/register")
    public Result<?> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(userService.register(request));
    }

    /** 用户登录：验证用户名密码，成功后返回 JWT Token 和用户信息 */
    @PostMapping("/login")
    public Result<?> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(userService.login(request));
    }
}
