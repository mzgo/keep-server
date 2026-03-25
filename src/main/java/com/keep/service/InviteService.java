package com.keep.service;

import com.keep.entity.ManagerConfig;
import com.keep.exception.BizException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
public class InviteService {

    private static final String INVITE_PREFIX = "invite:";
    private static final Duration INVITE_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final ManagerConfigService configService;

    public InviteService(StringRedisTemplate redisTemplate, ManagerConfigService configService) {
        this.redisTemplate = redisTemplate;
        this.configService = configService;
    }

    /**
     * 生成邀请链接 token (管理者必须已完成配置初始化)
     */
    public String generateInviteToken(Long managerId) {
        ManagerConfig config = configService.getConfig(managerId);
        if (!config.isInitialized()) {
            throw new BizException("请先完成规则配置");
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(INVITE_PREFIX + token, String.valueOf(managerId), INVITE_TTL);
        return token;
    }

    /**
     * 验证邀请 token 是否有效 (不消费)
     */
    public boolean isValid(String token) {
        return redisTemplate.hasKey(INVITE_PREFIX + token);
    }
}
