package com.keep.repository;

import com.keep.entity.CheckinRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CheckinRecordRepository extends JpaRepository<CheckinRecord, Long> {

    Optional<CheckinRecord> findByCheckerIdAndCheckinDate(Long checkerId, LocalDate checkinDate);

    boolean existsByCheckerIdAndCheckinDate(Long checkerId, LocalDate checkinDate);

    List<CheckinRecord> findByCheckerIdOrderByCheckinDateDesc(Long checkerId);

    /**
     * 获取指定日期范围内的打卡记录 (日历视图)
     */
    @Query("SELECT c FROM CheckinRecord c WHERE c.checkerId = :checkerId " +
            "AND c.checkinDate >= :startDate AND c.checkinDate <= :endDate " +
            "ORDER BY c.checkinDate ASC")
    List<CheckinRecord> findByCheckerIdAndDateRange(Long checkerId, LocalDate startDate, LocalDate endDate);

    /**
     * 获取最近一条打卡记录
     */
    Optional<CheckinRecord> findTopByCheckerIdOrderByCheckinDateDesc(Long checkerId);

    /**
     * 获取从某个日期开始的连续打卡记录（用于计算当前连续天数）
     */
    @Query("SELECT c FROM CheckinRecord c WHERE c.checkerId = :checkerId " +
            "AND c.checkinDate <= :fromDate ORDER BY c.checkinDate DESC")
    List<CheckinRecord> findRecentRecords(Long checkerId, LocalDate fromDate);

    List<CheckinRecord> findByCheckerIdAndManagerId(Long checkerId, Long managerId);

    boolean existsByPhotoKeyAndCheckerId(String photoKey, Long checkerId);

    boolean existsByPhotoKeyAndManagerId(String photoKey, Long managerId);
}
