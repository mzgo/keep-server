package com.keep.controller;

import com.keep.config.JwtFilter;
import com.keep.dto.ApiResponse;
import com.keep.entity.RedeemOrder;
import com.keep.exception.BizException;
import com.keep.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** 打卡者: 兑换奖品 */
    @PostMapping("/orders/redeem")
    public ApiResponse<RedeemOrder> redeem(HttpServletRequest request, @RequestBody Map<String, Long> body) {
        Long checkerId = requireAuth(request);
        Long prizeId = body.get("prizeId");
        if (prizeId == null) throw new BizException("请选择奖品");
        return ApiResponse.ok(orderService.redeem(checkerId, prizeId));
    }

    /** 打卡者: 取消订单 */
    @PostMapping("/orders/{id}/cancel")
    public ApiResponse<Map<String, Integer>> cancelOrder(HttpServletRequest request, @PathVariable Long id) {
        Long checkerId = requireAuth(request);
        int expiredAmount = orderService.cancelOrder(checkerId, id);
        return ApiResponse.ok(Map.of("expiredPointsAmount", expiredAmount));
    }

    /** 打卡者: 我的订单列表 */
    @GetMapping("/orders")
    public ApiResponse<List<RedeemOrder>> getMyOrders(HttpServletRequest request,
                                                       @RequestParam(required = false) String status) {
        Long checkerId = requireAuth(request);
        return ApiResponse.ok(orderService.getCheckerOrders(checkerId, status));
    }

    /** 管理者: 订单列表 */
    @GetMapping("/manager/orders")
    public ApiResponse<List<RedeemOrder>> getManagerOrders(HttpServletRequest request,
                                                            @RequestParam(required = false) String status) {
        Long managerId = requireManager(request);
        return ApiResponse.ok(orderService.getManagerOrders(managerId, status));
    }

    /** 管理者: 核销订单 */
    @PostMapping("/manager/orders/{id}/verify")
    public ApiResponse<RedeemOrder> verifyOrder(HttpServletRequest request,
                                                 @PathVariable Long id,
                                                 @RequestBody Map<String, String> body) {
        Long managerId = requireManager(request);
        String verifyToken = body.get("verifyToken");
        if (verifyToken == null) throw new BizException("缺少核销token");
        return ApiResponse.ok(orderService.verifyOrder(managerId, id, verifyToken));
    }

    private Long requireAuth(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(JwtFilter.ATTR_USER_ID);
        if (userId == null) throw new BizException(401, "请先登录");
        return userId;
    }

    private Long requireManager(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(JwtFilter.ATTR_USER_ID);
        String role = (String) request.getAttribute(JwtFilter.ATTR_ROLE);
        if (userId == null) throw new BizException(401, "请先登录");
        if (!"MANAGER".equals(role)) throw new BizException(403, "仅管理者可访问");
        return userId;
    }
}
