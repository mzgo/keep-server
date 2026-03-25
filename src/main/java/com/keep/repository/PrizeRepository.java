package com.keep.repository;

import com.keep.entity.Prize;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PrizeRepository extends JpaRepository<Prize, Long> {

    List<Prize> findByManagerIdAndArchivedFalseOrderByCreatedAtDesc(Long managerId);

    List<Prize> findByManagerIdOrderByCreatedAtDesc(Long managerId);

    boolean existsByImageKeyAndManagerId(String imageKey, Long managerId);
}
