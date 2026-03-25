package com.keep.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.keep.entity.Prize;
import com.keep.entity.RedeemOrder;
import com.keep.entity.User;
import com.keep.exception.BizException;
import com.keep.repository.PrizeRepository;
import com.keep.repository.RedeemOrderRepository;
import com.keep.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final RedeemOrderRepository orderRepository;
    private final PrizeRepository prizeRepository;
    private final UserRepository userRepository;
    private final PointsService pointsService;
    private final ObjectMapper objectMapper;

    public OrderService(RedeemOrderRepository orderRepository, PrizeRepository prizeRepository,
                        UserRepository userRepository, PointsService pointsService,
                        ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.prizeRepository = prizeRepository;
        this.userRepository = userRepository;
        this.pointsService = pointsService;
        this.objectMapper = objectMapper;
    }

    /**
     * 兑换奖品
     */
    @Transactional
    public RedeemOrder redeem(Long checkerId, Long prizeId) {
        User checker = userRepository.findById(checkerId)
                .orElseThrow(() -> new BizException("用户不存在"));
        if (checker.getManagerId() == null) {
            throw new BizException("未绑定管理者");
        }

        Prize prize = prizeRepository.findById(prizeId)
                .orElseThrow(() -> new BizException("奖品不存在"));

        if (prize.isArchived()) {
            throw new BizException("该奖品已下架");
        }
        if (!prize.getManagerId().equals(checker.getManagerId())) {
            throw new BizException("无法兑换其他管理者的奖品");
        }
        if (prize.getRemainingStock() <= 0) {
            throw new BizException("奖品已售罄");
        }

        // FIFO 消费积分
        List<PointsService.ConsumedDetail> details = pointsService.consumePoints(
                checkerId, prize.getRequiredPoints());

        // 扣库存
        prize.setRedeemedCount(prize.getRedeemedCount() + 1);
        try {
            // 提前 flush，尽早感知乐观锁冲突，避免超卖
            prizeRepository.saveAndFlush(prize);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BizException(409, "库存状态已变化，请重试兑换");
        }

        // 创建订单
        RedeemOrder order = new RedeemOrder();
        order.setCheckerId(checkerId);
        order.setManagerId(checker.getManagerId());
        order.setPrizeId(prizeId);
        order.setPrizeName(prize.getName());
        order.setPointsCost(prize.getRequiredPoints());
        order.setStatus("PENDING");
        order.setVerifyToken(UUID.randomUUID().toString().replace("-", ""));

        try {
            order.setConsumedDetails(objectMapper.writeValueAsString(details));
        } catch (Exception e) {
            throw new BizException("系统错误");
        }

        return orderRepository.save(order);
    }

    /**
     * 取消兑换
     */
    @Transactional
    public int cancelOrder(Long checkerId, Long orderId) {
        RedeemOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BizException("订单不存在"));

        if (!order.getCheckerId().equals(checkerId)) {
            throw new BizException(403, "无权操作此订单");
        }
        if (!"PENDING".equals(order.getStatus())) {
            throw new BizException("只有待核销的订单可以取消");
        }

        order.setStatus("CANCELLED");
        order.setCancelledAt(LocalDateTime.now());
        orderRepository.save(order);

        // 还原库存
        Prize prize = prizeRepository.findById(order.getPrizeId()).orElse(null);
        if (prize != null) {
            prize.setRedeemedCount(Math.max(0, prize.getRedeemedCount() - 1));
            prizeRepository.save(prize);
        }

        // 还原积分 (可能部分过期)
        int expiredAmount = 0;
        try {
            List<PointsService.ConsumedDetail> details = objectMapper.readValue(
                    order.getConsumedDetails(),
                    new TypeReference<>() {});
            expiredAmount = pointsService.refundPoints(checkerId, details);
        } catch (Exception e) {
            log.error("取消订单积分还原失败, orderId={}", orderId, e);
            throw new BizException(500, "订单取消失败，请稍后重试");
        }

        return expiredAmount;
    }

    /**
     * 核销订单 (管理者扫码)
     */
    @Transactional
    public RedeemOrder verifyOrder(Long managerId, Long orderId, String verifyToken) {
        RedeemOrder order = orderRepository.findByIdAndVerifyToken(orderId, verifyToken)
                .orElseThrow(() -> new BizException("无效的核销码"));

        if (!order.getManagerId().equals(managerId)) {
            throw new BizException(403, "无权核销此订单");
        }
        if (!"PENDING".equals(order.getStatus())) {
            throw new BizException("该订单已" + ("VERIFIED".equals(order.getStatus()) ? "核销" : "取消"));
        }

        order.setStatus("VERIFIED");
        order.setVerifiedAt(LocalDateTime.now());
        return orderRepository.save(order);
    }

    public List<RedeemOrder> getCheckerOrders(Long checkerId, String status) {
        if (status == null || "ALL".equals(status)) {
            return orderRepository.findByCheckerIdOrderByCreatedAtDesc(checkerId);
        }
        return orderRepository.findByCheckerIdAndStatusOrderByCreatedAtDesc(checkerId, status);
    }

    public List<RedeemOrder> getManagerOrders(Long managerId, String status) {
        if (status == null || "ALL".equals(status)) {
            return orderRepository.findByManagerIdOrderByCreatedAtDesc(managerId);
        }
        return orderRepository.findByManagerIdAndStatusOrderByCreatedAtDesc(managerId, status);
    }
}
