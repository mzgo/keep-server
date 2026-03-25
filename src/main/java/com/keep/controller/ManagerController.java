package com.keep.controller;

import com.keep.config.JwtFilter;
import com.keep.dto.ApiResponse;
import com.keep.dto.UserVO;
import com.keep.entity.ManagerConfig;
import com.keep.entity.User;
import com.keep.exception.BizException;
import com.keep.repository.UserRepository;
import com.keep.service.AuthService;
import com.keep.service.InviteService;
import com.keep.service.ManagerConfigService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/manager")
public class ManagerController {

    private final ManagerConfigService configService;
    private final InviteService inviteService;
    private final UserRepository userRepository;
    private final AuthService authService;

    public ManagerController(ManagerConfigService configService, InviteService inviteService,
                             UserRepository userRepository, AuthService authService) {
        this.configService = configService;
        this.inviteService = inviteService;
        this.userRepository = userRepository;
        this.authService = authService;
    }

    @GetMapping("/config")
    public ApiResponse<ManagerConfig> getConfig(HttpServletRequest request) {
        Long managerId = requireManager(request);
        return ApiResponse.ok(configService.getConfig(managerId));
    }

    @PutMapping("/config")
    public ApiResponse<ManagerConfig> saveConfig(HttpServletRequest request,
                                                 @RequestBody ManagerConfig update) {
        Long managerId = requireManager(request);
        return ApiResponse.ok(configService.saveConfig(managerId, update));
    }

    @PostMapping("/invite")
    public ApiResponse<Map<String, String>> generateInvite(HttpServletRequest request) {
        Long managerId = requireManager(request);
        String token = inviteService.generateInviteToken(managerId);
        return ApiResponse.ok(Map.of("inviteToken", token));
    }

    @GetMapping("/checkers")
    public ApiResponse<List<UserVO>> getCheckers(HttpServletRequest request) {
        Long managerId = requireManager(request);
        List<UserVO> checkers = userRepository.findByManagerId(managerId).stream()
                .map(u -> authService.getCurrentUser(u.getId()))
                .toList();
        return ApiResponse.ok(checkers);
    }

    private Long requireManager(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(JwtFilter.ATTR_USER_ID);
        String role = (String) request.getAttribute(JwtFilter.ATTR_ROLE);
        if (userId == null) throw new BizException(401, "请先登录");
        if (!"MANAGER".equals(role)) throw new BizException(403, "仅管理者可访问");
        return userId;
    }
}
