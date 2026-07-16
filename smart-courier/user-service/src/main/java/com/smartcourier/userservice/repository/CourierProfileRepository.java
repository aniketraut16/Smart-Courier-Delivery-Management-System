package com.smartcourier.userservice.repository;

import com.smartcourier.userservice.model.AvailabilityStatus;
import com.smartcourier.userservice.model.CourierProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CourierProfileRepository extends JpaRepository<CourierProfile, UUID> {

    /**
     * Find a CourierProfile by its associated User's ID.
     * Uses an explicit @Query to avoid Spring Data ambiguity on nested-association navigation.
     */
    @Query("SELECT cp FROM CourierProfile cp WHERE cp.user.id = :userId")
    Optional<CourierProfile> findByUserId(@Param("userId") UUID userId);

    /**
     * Find all CourierProfiles with a given availability status.
     * Used by the internal endpoint to return available couriers.
     */
    List<CourierProfile> findByAvailabilityStatus(AvailabilityStatus availabilityStatus);
}
