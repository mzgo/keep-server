package com.keep.repository;

import com.keep.entity.ManagerConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ManagerConfigRepository extends JpaRepository<ManagerConfig, Long> {

    Optional<ManagerConfig> findByManagerId(Long managerId);
}
