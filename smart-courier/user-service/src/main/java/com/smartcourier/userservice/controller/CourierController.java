package com.smartcourier.userservice.controller;

import com.smartcourier.userservice.dto.CourierAvailabilityResponse;
import com.smartcourier.userservice.dto.UpdateAvailabilityRequest;
import com.smartcourier.userservice.service.CourierService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Courier Availability", description = "Manage courier availability status")
@RestController
@RequestMapping("/api/couriers")
public class CourierController {

    private final CourierService courierService;

    public CourierController(CourierService courierService) {
        this.courierService = courierService;
    }

    @Operation(summary = "Get the authenticated courier's availability status")
    @PreAuthorize("hasRole('COURIER')")
    @GetMapping("/me/availability")
    public ResponseEntity<CourierAvailabilityResponse> getMyAvailability(Authentication authentication) {
        UUID userId = extractUserId(authentication);
        return ResponseEntity.ok(courierService.getAvailability(userId));
    }

    @Operation(summary = "Update the authenticated courier's availability status (AVAILABLE or OFFLINE only)")
    @PreAuthorize("hasRole('COURIER')")
    @PutMapping("/me/availability")
    public ResponseEntity<CourierAvailabilityResponse> updateMyAvailability(
            @Valid @RequestBody UpdateAvailabilityRequest request,
            Authentication authentication) {
        UUID userId = extractUserId(authentication);
        return ResponseEntity.ok(courierService.updateAvailability(userId, request));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private UUID extractUserId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
