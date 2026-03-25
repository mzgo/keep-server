package com.keep.repository;

import com.keep.entity.PointsTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PointsTransactionRepository extends JpaRepository<PointsTransaction, Long> {

    List<PointsTransaction> findByCheckerIdOrderByCreatedAtDesc(Long checkerId);
}
