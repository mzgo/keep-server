package com.keep.controller;

import com.keep.config.JwtFilter;
import com.keep.dto.ApiResponse;
import com.keep.entity.PointsTransaction;
import com.keep.exception.BizException;
import com.keep.service.PointsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/points")
public class PointsController {

    private final PointsService pointsService;

    public PointsController(PointsService pointsService) {
        this.pointsService = pointsService;
    }

    @GetMapping("/balance")
    public ApiResponse<Map<String, Integer>> getBalance(HttpServletRequest request) {
        Long userId = requireAuth(request);
        int available = pointsService.getAvailablePoints(userId);
        int expiring = pointsService.getExpiringPointsIn30Days(userId);
        return ApiResponse.ok(Map.of("available", available, "expiringSoon", expiring));
    }

    @GetMapping("/transactions")
    public ApiResponse<List<PointsTransaction>> getTransactions(HttpServletRequest request) {
        Long userId = requireAuth(request);
        return ApiResponse.ok(pointsService.getTransactions(userId));
    }

    @GetMapping("/expiring")
    public ApiResponse<Map<String, Integer>> getExpiring(HttpServletRequest request) {
        Long userId = requireAuth(request);
        int expiring = pointsService.getExpiringPointsIn30Days(userId);
        return ApiResponse.ok(Map.of("expiringSoon", expiring));
    }

    private Long requireAuth(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(JwtFilter.ATTR_USER_ID);
        if (userId == null) throw new BizException(401, "请先登录");
        return userId;
    }
}
