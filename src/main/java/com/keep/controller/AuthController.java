package com.keep.controller;

import com.keep.config.JwtFilter;
import com.keep.dto.*;
import com.keep.exception.BizException;
import com.keep.service.AuthService;
import com.keep.service.CaptchaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CaptchaService captchaService;

    public AuthController(AuthService authService, CaptchaService captchaService) {
        this.authService = authService;
        this.captchaService = captchaService;
    }

    @GetMapping("/captcha")
    public ApiResponse<Map<String, String>> getCaptcha() {
        return ApiResponse.ok(captchaService.generate());
    }

    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest req) {
        authService.register(req);
        return ApiResponse.ok();
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, String>> login(@Valid @RequestBody LoginRequest req) {
        String token = authService.login(req);
        return ApiResponse.ok(Map.of("token", token));
    }

    @GetMapping("/me")
    public ApiResponse<UserVO> getCurrentUser(HttpServletRequest request) {
        Long userId = requireAuth(request);
        return ApiResponse.ok(authService.getCurrentUser(userId));
    }

    @PutMapping("/profile")
    public ApiResponse<Void> updateProfile(HttpServletRequest request,
                                           @Valid @RequestBody UpdateProfileRequest req) {
        Long userId = requireAuth(request);
        authService.updateProfile(userId, req);
        return ApiResponse.ok();
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(HttpServletRequest request,
                                            @Valid @RequestBody ChangePasswordRequest req) {
        Long userId = requireAuth(request);
        authService.changePassword(userId, req);
        return ApiResponse.ok();
    }

    @PostMapping("/password-reset/request")
    public ApiResponse<Void> requestPasswordReset(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        authService.requestPasswordReset(email);
        return ApiResponse.ok();
    }

    @PostMapping("/password-reset/confirm")
    public ApiResponse<Void> confirmPasswordReset(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        String newPassword = body.get("newPassword");
        if (token == null || newPassword == null) {
            throw new BizException("参数不完整");
        }
        authService.confirmPasswordReset(token, newPassword);
        return ApiResponse.ok();
    }

    @PostMapping("/email/code")
    public ApiResponse<Void> sendEmailCode(HttpServletRequest request, @RequestBody Map<String, String> body) {
        Long userId = requireAuth(request);
        String email = body.get("email");
        authService.sendEmailCode(userId, email);
        return ApiResponse.ok();
    }

    @PostMapping("/email/bind")
    public ApiResponse<Void> bindEmail(HttpServletRequest request, @RequestBody Map<String, String> body) {
        Long userId = requireAuth(request);
        String email = body.get("email");
        String code = body.get("code");
        authService.bindEmail(userId, email, code);
        return ApiResponse.ok();
    }

    /**
     * 从 request attribute 中获取当前用户ID (由JwtFilter设置)
     */
    private Long requireAuth(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(JwtFilter.ATTR_USER_ID);
        if (userId == null) {
            throw new BizException(401, "请先登录");
        }
        return userId;
    }
}
