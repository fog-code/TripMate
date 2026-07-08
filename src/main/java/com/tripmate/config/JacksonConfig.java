package com.tripmate.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Jackson 序列化配置。
 *
 * <p>使用 @Primary 强制替换自动配置的 ObjectMapper，
 * 确保 {@code JavaTimeModule} 在 LocalDateTime 序列化时一定被注册。</p>
 */
@Configuration
public class JacksonConfig {

    /**
     * 提供全局 ObjectMapper，基于 Spring Boot 的 Jackson2ObjectMapperBuilder 构建，
     * 保留所有自动配置默认项（日期格式、时区等），并额外注入 JavaTimeModule。
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        return builder
                .modules(new JavaTimeModule())
                .build();
    }
}
