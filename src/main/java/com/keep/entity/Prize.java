package com.keep.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "prizes")
public class Prize {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "manager_id", nullable = false)
    private Long managerId;

    @Column(nullable = false, length = 100)
    private String name;

    /** 兑换所需积分 */
    @Column(name = "required_points", nullable = false)
    private int requiredPoints;

    /** 总库存 */
    @Column(name = "total_stock", nullable = false)
    private int totalStock;

    /** 已兑换（未取消）的数量 */
    @Column(name = "redeemed_count", nullable = false)
    private int redeemedCount = 0;

    /** 乐观锁版本号，用于并发兑换防超卖 */
    @Version
    private Long version;

    /** 图片七牛云 file key (可选) */
    @Column(name = "image_key", length = 200)
    private String imageKey;

    /** 是否已下架 (软删除) */
    @Column(nullable = false)
    private boolean archived = false;

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

    /** 剩余库存 = 总库存 - 已兑换数量 */
    @Transient
    public int getRemainingStock() {
        return totalStock - redeemedCount;
    }

    // -- Getters & Setters --

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getManagerId() { return managerId; }
    public void setManagerId(Long managerId) { this.managerId = managerId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getRequiredPoints() { return requiredPoints; }
    public void setRequiredPoints(int requiredPoints) { this.requiredPoints = requiredPoints; }

    public int getTotalStock() { return totalStock; }
    public void setTotalStock(int totalStock) { this.totalStock = totalStock; }

    public int getRedeemedCount() { return redeemedCount; }
    public void setRedeemedCount(int redeemedCount) { this.redeemedCount = redeemedCount; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public String getImageKey() { return imageKey; }
    public void setImageKey(String imageKey) { this.imageKey = imageKey; }

    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
