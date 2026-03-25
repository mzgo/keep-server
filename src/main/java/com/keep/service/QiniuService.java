package com.keep.service;

import com.keep.config.QiniuConfig;
import com.qiniu.storage.BucketManager;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.Region;
import com.qiniu.util.Auth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.util.UUID;

@Service
public class QiniuService {

    private static final Logger log = LoggerFactory.getLogger(QiniuService.class);

    private final QiniuConfig config;
    private final Auth auth;
    private final BucketManager bucketManager;

    public QiniuService(QiniuConfig config) {
        this.config = config;
        this.auth = Auth.create(config.accessKey(), config.secretKey());
        Configuration cfg = new Configuration(Region.autoRegion());
        this.bucketManager = new BucketManager(auth, cfg);
    }

    /**
     * 生成上传凭证
     */
    public UploadCredential generateUploadToken(String prefix) {
        String key = prefix + "/" + UUID.randomUUID().toString().replace("-", "");
        String token = auth.uploadToken(config.bucket(), key, config.uploadTokenExpire(), null);
        return new UploadCredential(token, key);
    }

    /**
     * 生成私有空间的签名访问URL
     */
    public String getSignedUrl(String fileKey) {
        if (fileKey == null || fileKey.isBlank()) return null;
        String baseUrl = "https://" + config.domain() + "/" + fileKey;
        return auth.privateDownloadUrl(baseUrl, config.signedUrlExpire());
    }

    /**
     * 异步删除七牛云文件
     */
    @Async
    public void deleteFileAsync(String fileKey) {
        if (fileKey == null || fileKey.isBlank()) return;
        try {
            bucketManager.delete(config.bucket(), fileKey);
            log.info("已删除七牛云文件: {}", fileKey);
        } catch (Exception e) {
            log.error("删除七牛云文件失败: {}, 错误: {}", fileKey, e.getMessage());
            // TODO: 记录失败日志，定时任务兜底重试
        }
    }

    public record UploadCredential(String token, String key) {}
}
