package com.keep.repository;

import com.keep.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByAvatarKey(String avatarKey);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
    boolean existsByEmailAndIdNot(String email, Long id);

    List<User> findByManagerId(Long managerId);
}
