package com.keep.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({JwtConfig.class, QiniuConfig.class})
public class AppConfig {
}
