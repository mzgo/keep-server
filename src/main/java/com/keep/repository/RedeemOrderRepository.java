package com.keep.repository;

import com.keep.entity.RedeemOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RedeemOrderRepository extends JpaRepository<RedeemOrder, Long> {

    List<RedeemOrder> findByCheckerIdOrderByCreatedAtDesc(Long checkerId);

    List<RedeemOrder> findByCheckerIdAndStatusOrderByCreatedAtDesc(Long checkerId, String status);

    List<RedeemOrder> findByManagerIdOrderByCreatedAtDesc(Long managerId);

    List<RedeemOrder> findByManagerIdAndStatusOrderByCreatedAtDesc(Long managerId, String status);

    Optional<RedeemOrder> findByIdAndVerifyToken(Long id, String verifyToken);

    /** 统计某奖品的有效（未取消）订单数 */
    int countByPrizeIdAndStatusNot(Long prizeId, String excludeStatus);
}
