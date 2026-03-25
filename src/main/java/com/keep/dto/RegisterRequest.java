package com.keep.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 50, message = "用户名长度3-50位")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 100, message = "密码长度6-100位")
        String password,

        @NotBlank(message = "验证码不能为空")
        String captchaCode,

        @NotBlank(message = "验证码ID不能为空")
        String captchaId,

        String email,

        // 邀请链接中的token，有值则注册为打卡者
        String inviteToken
) {}
