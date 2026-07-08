package com.tripmate.config;

import com.tripmate.common.UserContext;
import com.tripmate.dto.CachedUser;
import com.tripmate.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JWT 认证过滤器，每个请求只执行一次。
 *
 * <p>认证流程：
 * <ol>
 *   <li>从 Authorization 头提取 Bearer Token</li>
 *   <li>校验 Token 签名和有效期</li>
 *   <li>解析 userId，从 Redis/MySQL 加载用户信息</li>
 *   <li>将用户信息注入 Spring Security Context 和 UserContext（ThreadLocal）</li>
 *   <li>请求结束后在 finally 中清理 UserContext</li>
 * </ol></p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserService userService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // 1. 提取 Token
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        // 2. 校验 Token
        String token = header.substring(7);
        if (!jwtUtil.validateToken(token)) {
            log.debug("JWT token无效 path={}", request.getRequestURI());
            chain.doFilter(request, response);
            return;
        }

        // 3. 加载用户（优先 Redis 缓存，兜底 MySQL）
        Long userId = jwtUtil.getUserIdFromToken(token);
        CachedUser cachedUser = userService.getUserById(userId);
        if (cachedUser == null) {
            log.debug("JWT用户不存在 userId={} path={}", userId, request.getRequestURI());
            chain.doFilter(request, response);
            return;
        }

        // 4. 构建认证对象，注入 Spring Security Context
        List<SimpleGrantedAuthority> authorities = cachedUser.getAuthorities().stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(cachedUser, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 5. 注入 ThreadLocal，方便业务代码直接获取当前用户
        UserContext.set(cachedUser);
        try {
            chain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }
}
