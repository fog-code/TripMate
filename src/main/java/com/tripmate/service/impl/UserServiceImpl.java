package com.tripmate.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tripmate.config.JwtUtil;
import com.tripmate.dto.CachedUser;
import com.tripmate.dto.LoginRequest;
import com.tripmate.dto.RegisterRequest;
import com.tripmate.entity.User;
import com.tripmate.mapper.UserMapper;
import com.tripmate.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String USER_CACHE_KEY = "user:%d";
    private static final long CACHE_TTL_SECONDS = 604800;

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public Map<String, Object> register(RegisterRequest request) {
        if (userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername())) > 0) {
            log.warn("注册失败：用户名已存在 username={}", request.getUsername());
            throw new RuntimeException("用户名已存在");
        }
        if (userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getEmail, request.getEmail())) > 0) {
            log.warn("注册失败：邮箱已被注册 email={}", request.getEmail());
            throw new RuntimeException("邮箱已被注册");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setRole("USER");
        user.setStatus(1);
        userMapper.insert(user);

        cacheUser(user);
        String token = jwtUtil.generateToken(user.getId());

        log.info("用户注册成功 userId={} username={}", user.getId(), user.getUsername());
        return Map.of(
            "userId", user.getId(),
            "username", user.getUsername(),
            "role", user.getRole() != null ? user.getRole() : "USER",
            "token", token
        );
    }

    @Override
    public Map<String, Object> login(LoginRequest request) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));
        if (user == null) {
            log.warn("登录失败：用户名不存在 username={}", request.getUsername());
            throw new RuntimeException("用户名或密码错误");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("登录失败：密码错误 userId={} username={}", user.getId(), user.getUsername());
            throw new RuntimeException("用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            log.warn("登录失败：账号已禁用 userId={} username={}", user.getId(), user.getUsername());
            throw new RuntimeException("账号已被禁用");
        }

        cacheUser(user);
        String token = jwtUtil.generateToken(user.getId());

        log.info("用户登录成功 userId={} username={} role={}", user.getId(), user.getUsername(),
                user.getRole() != null ? user.getRole() : "USER");
        return Map.of(
            "userId", user.getId(),
            "username", user.getUsername(),
            "role", user.getRole() != null ? user.getRole() : "USER",
            "token", token
        );
    }

    @Override
    public CachedUser getUserById(Long userId) {
        String key = String.format(USER_CACHE_KEY, userId);
        CachedUser cached = (CachedUser) redisTemplate.opsForValue().get(key);
        if (cached != null) {
            log.debug("用户缓存命中 userId={}", userId);
            return cached;
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            log.debug("用户不存在 userId={}", userId);
            return null;
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            log.debug("用户已禁用 userId={}", userId);
            return null;
        }

        cached = cacheUser(user);
        log.debug("用户缓存回填 userId={}", userId);
        return cached;
    }

    private CachedUser cacheUser(User user) {
        CachedUser cached = new CachedUser();
        cached.setId(user.getId());
        cached.setUsername(user.getUsername());
        String role = user.getRole() != null ? user.getRole() : "USER";
        cached.setAuthorities(List.of("ROLE_" + role));

        String key = String.format(USER_CACHE_KEY, user.getId());
        redisTemplate.opsForValue().set(key, cached, Duration.ofSeconds(CACHE_TTL_SECONDS));
        return cached;
    }
}
