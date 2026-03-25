package com.keep.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;
    private final String[] allowedMethods;
    private final boolean allowCredentials;
    private final long maxAge;

    public WebConfig(
            @Value("${app.cors.allowed-origins:http://localhost:3000}") String allowedOrigins,
            @Value("${app.cors.allowed-methods:GET,POST,PUT,DELETE,OPTIONS}") String allowedMethods,
            @Value("${app.cors.allow-credentials:true}") boolean allowCredentials,
            @Value("${app.cors.max-age:3600}") long maxAge
    ) {
        this.allowedOrigins = parseCsv(allowedOrigins);
        this.allowedMethods = parseCsv(allowedMethods);
        this.allowCredentials = allowCredentials;
        this.maxAge = maxAge;

        // 允许携带凭证时，不能使用通配来源，避免高风险配置。
        if (allowCredentials && Arrays.stream(this.allowedOrigins).anyMatch("*"::equals)) {
            throw new IllegalStateException("CORS 配置非法：allow-credentials=true 时不允许 allowed-origins 包含 *");
        }
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods(allowedMethods)
                .allowedHeaders("*")
                .allowCredentials(allowCredentials)
                .maxAge(maxAge);
    }

    private String[] parseCsv(String raw) {
        String[] values = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toArray(String[]::new);
        if (values.length == 0) {
            throw new IllegalStateException("CORS 配置非法：allowed-origins/allowed-methods 不能为空");
        }
        return values;
    }
}
