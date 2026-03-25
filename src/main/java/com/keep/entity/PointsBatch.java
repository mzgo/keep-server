package com.keep.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 积分逐笔记录 - 支持按过期时间 FIFO 消费
 */
@Entity
@Table(name = "points_batches")
public class PointsBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "checker_id", nullable = false)
    private Long checkerId;

    @Column(name = "manager_id", nullable = false)
    private Long managerId;

    /** 初始积分数量 */
    @Column(name = "original_amount", nullable = false)
    private int originalAmount;

    /** 剩余可用积分 */
    @Column(name = "remaining_amount", nullable = false)
    private int remainingAmount;

    /** 来源: CYCLE_REWARD / BONUS_REWARD */
    @Column(nullable = false, length = 30)
    private String source;

    /** 过期日期 */
    @Column(name = "expire_date", nullable = false)
    private LocalDate expireDate;

    /** 是否已过期 */
    @Column(nullable = false)
    private boolean expired = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }

    // -- Getters & Setters --

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCheckerId() { return checkerId; }
    public void setCheckerId(Long checkerId) { this.checkerId = checkerId; }

    public Long getManagerId() { return managerId; }
    public void setManagerId(Long managerId) { this.managerId = managerId; }

    public int getOriginalAmount() { return originalAmount; }
    public void setOriginalAmount(int originalAmount) { this.originalAmount = originalAmount; }

    public int getRemainingAmount() { return remainingAmount; }
    public void setRemainingAmount(int remainingAmount) { this.remainingAmount = remainingAmount; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public LocalDate getExpireDate() { return expireDate; }
    public void setExpireDate(LocalDate expireDate) { this.expireDate = expireDate; }

    public boolean isExpired() { return expired; }
    public void setExpired(boolean expired) { this.expired = expired; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
