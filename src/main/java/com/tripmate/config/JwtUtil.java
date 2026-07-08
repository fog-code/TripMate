package com.tripmate.config;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 令牌工具类。
 *
 * <p>负责 Token 的生成、解析和校验。Token 的 subject 存储 userId，
 * 过期时间由配置文件 {@code jwt.expiration} 控制（默认 604800000ms = 7 天）。</p>
 */
@Component
public class JwtUtil {

    /** HMAC-SHA256 签名密钥 */
    private final SecretKey key;

    /** Token 有效时长（毫秒） */
    private final long expiration;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expiration}") long expiration) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
    }

    /** 为用户生成 JWT Token，payload 中包含 userId 和过期时间 */
    public String generateToken(Long userId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    /** 从 Token 中解析出 userId */
    public Long getUserIdFromToken(String token) {
        String subject = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
        return Long.valueOf(subject);
    }

    /** 校验 Token 是否合法（签名正确 + 未过期） */
    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
