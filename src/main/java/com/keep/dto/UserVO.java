package com.keep.dto;

/**
 * 用户信息视图对象 (不含密码等敏感字段)
 */
public record UserVO(
        Long id,
        String username,
        String nickname,
        String avatarUrl,
        String email,
        boolean emailVerified,
        String role,
        Long managerId,
        String managerNickname
) {}
