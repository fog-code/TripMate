package com.tripmate.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 核心配置。
 *
 * <p>采用无状态 JWT 鉴权模式：
 * <ul>
 *   <li>禁用 CSRF（前后端分离，通过 Token 防跨站）</li>
 *   <li>无 Session（STALESS，每次请求独立认证）</li>
 *   <li>公开接口放行，其余全部需认证</li>
 *   <li>JWT 过滤器插入 UsernamePasswordAuthenticationFilter 之前</li>
 *   <li>启用方法级安全注解 @PreAuthorize（用于管理员接口）</li>
 * </ul></p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** BCrypt 密码编码器，用于注册时加密、登录时校验 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 安全过滤器链配置。
     *
     * <p>公开接口（无需 Token）：
     * <ul>
     *   <li>POST /api/users/register, /api/users/login —— 注册登录</li>
     *   <li>GET /api/attractions/** —— 景点列表和详情</li>
     *   <li>GET /api/recommendations —— 推荐结果</li>
     *   <li>/api/ai/** —— AI 助手</li>
     *   <li>/api/destination/route —— 目的地路线</li>
     *   <li>/api/location/**, /api/place/** —— IP 定位、地点搜索</li>
     * </ul></p>
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationFilter jwtFilter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // 公开接口
                .requestMatchers("/api/users/register", "/api/users/login").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/attractions/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/recommendations").permitAll()
                .requestMatchers("/api/ai/**").permitAll()
                .requestMatchers("/api/destination/route").permitAll()
                .requestMatchers("/api/location/**", "/api/place/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
