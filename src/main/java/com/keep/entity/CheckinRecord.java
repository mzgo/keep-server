package com.keep.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "checkin_records", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"checker_id", "checkin_date"})
})
public class CheckinRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "checker_id", nullable = false)
    private Long checkerId;

    @Column(name = "manager_id", nullable = false)
    private Long managerId;

    /** 打卡日日期 (根据 resetHour 计算的逻辑日期) */
    @Column(name = "checkin_date", nullable = false)
    private LocalDate checkinDate;

    /** 照片在七牛云的 file key */
    @Column(name = "photo_key", nullable = false, length = 200)
    private String photoKey;

    /** 打卡附带文字 (可选) */
    @Column(length = 500)
    private String note;

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

    public LocalDate getCheckinDate() { return checkinDate; }
    public void setCheckinDate(LocalDate checkinDate) { this.checkinDate = checkinDate; }

    public String getPhotoKey() { return photoKey; }
    public void setPhotoKey(String photoKey) { this.photoKey = photoKey; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
