package com.keep.dto;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(max = 50, message = "昵称最长50位")
        String nickname,

        // 头像的七牛云 file key
        String avatarKey
) {}
