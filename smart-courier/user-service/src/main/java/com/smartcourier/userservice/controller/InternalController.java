package com.smartcourier.userservice.controller;

import com.smartcourier.userservice.dto.InternalCourierResponse;
import com.smartcourier.userservice.dto.InternalCustomerResponse;
import com.smartcourier.userservice.service.CourierService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Internal service-to-service endpoints.
 * Access is enforced entirely by InternalApiKeyFilter (X-Internal-Api-Key header).
 * No Spring Security authentication is applied to this path.
 */
@Tag(name = "Internal API", description = "Service-to-service endpoints protected by API key")
@RestController
@RequestMapping("/internal")
public class InternalController {

    private final CourierService courierService;

    public InternalController(CourierService courierService) {
        this.courierService = courierService;
    }

    @Operation(summary = "Get minimal customer info by ID — for internal service calls")
    @GetMapping("/customers/{id}")
    public ResponseEntity<InternalCustomerResponse> getCustomer(@PathVariable UUID id) {
        // UserNotFoundException → 404 via GlobalExceptionHandler
        InternalCustomerResponse response = courierService.getCustomerById(id);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get all couriers with AVAILABLE status — for internal service calls")
    @GetMapping("/couriers/available")
    public ResponseEntity<List<InternalCourierResponse>> getAvailableCouriers() {
        return ResponseEntity.ok(courierService.getAvailableCouriers());
    }
}
