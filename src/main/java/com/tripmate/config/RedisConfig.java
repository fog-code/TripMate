package com.tripmate.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 序列化配置。
 *
 * <p>定义 RedisTemplate Bean，key 用 String 序列化，value 用 JSON 序列化。
 * 显式注册 JavaTimeModule 以支持 LocalDateTime 等时间类型的序列化/反序列化，
 * 启用默认类型信息确保反序列化时能还原为具体 Java 类型。</p>
 *
 * <p>缓存策略：
 * <ul>
 *   <li>user:${id} —— 用户信息（7 天）</li>
 *   <li>attraction:${id} —— 景点详情（1 小时）</li>
 *   <li>recommend:v2:user:${uid}:${limit} —— 个性化推荐（1 分钟）</li>
 *   <li>recommend:v2:global:${limit} —— 全局热门（1 分钟）</li>
 * </ul></p>
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // 显式注册 JavaTimeModule 以支持 LocalDateTime 等 Java 8 时间类型序列化
        // 启用默认类型信息，反序列化时自动还原为具体类型（如 CachedUser、Attraction）
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        objectMapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL);

        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);
        template.afterPropertiesSet();
        return template;
    }
}
