package com.keep.repository;

import com.keep.entity.PointsBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface PointsBatchRepository extends JpaRepository<PointsBatch, Long> {

    /** 可用积分批次，按过期时间升序 (FIFO) */
    @Query("SELECT b FROM PointsBatch b WHERE b.checkerId = :checkerId " +
            "AND b.remainingAmount > 0 AND b.expired = false " +
            "ORDER BY b.expireDate ASC")
    List<PointsBatch> findAvailableBatches(Long checkerId);

    /** 查询即将在指定天数内过期的积分 */
    @Query("SELECT COALESCE(SUM(b.remainingAmount), 0) FROM PointsBatch b " +
            "WHERE b.checkerId = :checkerId AND b.remainingAmount > 0 AND b.expired = false " +
            "AND b.expireDate <= :deadline")
    int sumExpiringPoints(Long checkerId, LocalDate deadline);

    /** 查询当前可用积分总数 */
    @Query("SELECT COALESCE(SUM(b.remainingAmount), 0) FROM PointsBatch b " +
            "WHERE b.checkerId = :checkerId AND b.remainingAmount > 0 AND b.expired = false")
    int sumAvailablePoints(Long checkerId);

    /** 查询已过期但未标记的批次 */
    @Query("SELECT b FROM PointsBatch b WHERE b.expired = false AND b.remainingAmount > 0 " +
            "AND b.expireDate < :today")
    List<PointsBatch> findExpiredBatches(LocalDate today);
}
