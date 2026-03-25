package com.keep.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "qiniu")
public record QiniuConfig(
        String accessKey,
        String secretKey,
        String bucket,
        String domain,
        int uploadTokenExpire,
        int signedUrlExpire
) {
}
