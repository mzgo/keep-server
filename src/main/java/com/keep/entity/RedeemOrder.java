package com.keep.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "redeem_orders")
public class RedeemOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "checker_id", nullable = false)
    private Long checkerId;

    @Column(name = "manager_id", nullable = false)
    private Long managerId;

    @Column(name = "prize_id", nullable = false)
    private Long prizeId;

    @Column(name = "prize_name", nullable = false, length = 100)
    private String prizeName;

    /** 兑换消耗的积分 */
    @Column(name = "points_cost", nullable = false)
    private int pointsCost;

    /** PENDING / VERIFIED / CANCELLED */
    @Column(nullable = false, length = 20)
    private String status;

    /** 核销用的随机token */
    @Column(name = "verify_token", nullable = false, unique = true, length = 64)
    private String verifyToken;

    /** 积分消费明细 JSON (用于取消时还原) */
    @Column(name = "consumed_details", length = 2000)
    private String consumedDetails;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // -- Getters & Setters --

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCheckerId() { return checkerId; }
    public void setCheckerId(Long checkerId) { this.checkerId = checkerId; }

    public Long getManagerId() { return managerId; }
    public void setManagerId(Long managerId) { this.managerId = managerId; }

    public Long getPrizeId() { return prizeId; }
    public void setPrizeId(Long prizeId) { this.prizeId = prizeId; }

    public String getPrizeName() { return prizeName; }
    public void setPrizeName(String prizeName) { this.prizeName = prizeName; }

    public int getPointsCost() { return pointsCost; }
    public void setPointsCost(int pointsCost) { this.pointsCost = pointsCost; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getVerifyToken() { return verifyToken; }
    public void setVerifyToken(String verifyToken) { this.verifyToken = verifyToken; }

    public String getConsumedDetails() { return consumedDetails; }
    public void setConsumedDetails(String consumedDetails) { this.consumedDetails = consumedDetails; }

    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(LocalDateTime verifiedAt) { this.verifiedAt = verifiedAt; }

    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
