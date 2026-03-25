package com.keep.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 积分变动流水
 */
@Entity
@Table(name = "points_transactions")
public class PointsTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "checker_id", nullable = false)
    private Long checkerId;

    /** EARN / CONSUME / EXPIRE / PENALTY / REFUND */
    @Column(nullable = false, length = 20)
    private String type;

    /** 正数=获得，负数=消耗 */
    @Column(nullable = false)
    private int amount;

    /** 变动后的余额 */
    @Column(name = "balance_after", nullable = false)
    private int balanceAfter;

    /** 备注 */
    @Column(length = 200)
    private String remark;

    /** 关联的 PointsBatch ID (可选) */
    @Column(name = "batch_id")
    private Long batchId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }

    // -- Getters & Setters --

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCheckerId() { return checkerId; }
    public void setCheckerId(Long checkerId) { this.checkerId = checkerId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public int getAmount() { return amount; }
    public void setAmount(int amount) { this.amount = amount; }

    public int getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(int balanceAfter) { this.balanceAfter = balanceAfter; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
