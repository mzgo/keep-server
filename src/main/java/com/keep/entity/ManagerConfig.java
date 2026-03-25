package com.keep.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 管理者的规则配置 - 每个管理者一份
 */
@Entity
@Table(name = "manager_configs")
public class ManagerConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "manager_id", nullable = false, unique = true)
    private Long managerId;

    /** 连续打卡天数 (默认5) */
    @Column(name = "streak_days", nullable = false)
    private int streakDays = 5;

    /** 每周期积分 (默认1) */
    @Column(name = "points_per_cycle", nullable = false)
    private int pointsPerCycle = 1;

    /** 额外奖励所需全勤次数 (默认3) */
    @Column(name = "bonus_full_count", nullable = false)
    private int bonusFullCount = 3;

    /** 额外奖励积分 (默认1) */
    @Column(name = "bonus_points", nullable = false)
    private int bonusPoints = 1;

    /** 积分有效期(天) (默认365) */
    @Column(name = "points_validity", nullable = false)
    private int pointsValidity = 365;

    /** 惩罚触发天数 (默认10) */
    @Column(name = "penalty_days", nullable = false)
    private int penaltyDays = 10;

    /** 惩罚扣减积分 (默认1) */
    @Column(name = "penalty_points", nullable = false)
    private int penaltyPoints = 1;

    /** 每日重置小时 (UTC+8, 默认5, 即凌晨5点) */
    @Column(name = "reset_hour", nullable = false)
    private int resetHour = 5;

    /** 配置是否已完成初始化 */
    @Column(name = "initialized", nullable = false)
    private boolean initialized = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // -- Getters & Setters --

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getManagerId() { return managerId; }
    public void setManagerId(Long managerId) { this.managerId = managerId; }

    public int getStreakDays() { return streakDays; }
    public void setStreakDays(int streakDays) { this.streakDays = streakDays; }

    public int getPointsPerCycle() { return pointsPerCycle; }
    public void setPointsPerCycle(int pointsPerCycle) { this.pointsPerCycle = pointsPerCycle; }

    public int getBonusFullCount() { return bonusFullCount; }
    public void setBonusFullCount(int bonusFullCount) { this.bonusFullCount = bonusFullCount; }

    public int getBonusPoints() { return bonusPoints; }
    public void setBonusPoints(int bonusPoints) { this.bonusPoints = bonusPoints; }

    public int getPointsValidity() { return pointsValidity; }
    public void setPointsValidity(int pointsValidity) { this.pointsValidity = pointsValidity; }

    public int getPenaltyDays() { return penaltyDays; }
    public void setPenaltyDays(int penaltyDays) { this.penaltyDays = penaltyDays; }

    public int getPenaltyPoints() { return penaltyPoints; }
    public void setPenaltyPoints(int penaltyPoints) { this.penaltyPoints = penaltyPoints; }

    public int getResetHour() { return resetHour; }
    public void setResetHour(int resetHour) { this.resetHour = resetHour; }

    public boolean isInitialized() { return initialized; }
    public void setInitialized(boolean initialized) { this.initialized = initialized; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
