package com.smartcourier.userservice.repository;

import com.smartcourier.userservice.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);

    // Page<User> findAll(Pageable pageable) is inherited from JpaRepository — usable as-is.
    // Declared here explicitly for documentation clarity.
    @Override
    Page<User> findAll(Pageable pageable);
}
