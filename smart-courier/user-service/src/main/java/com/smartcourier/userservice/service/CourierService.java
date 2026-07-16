package com.smartcourier.userservice.service;

import com.smartcourier.userservice.dto.CourierAvailabilityResponse;
import com.smartcourier.userservice.dto.InternalCourierResponse;
import com.smartcourier.userservice.dto.InternalCustomerResponse;
import com.smartcourier.userservice.dto.UpdateAvailabilityRequest;
import com.smartcourier.userservice.exception.CourierProfileNotFoundException;
import com.smartcourier.userservice.exception.InvalidAvailabilityTransitionException;
import com.smartcourier.userservice.exception.UserNotFoundException;
import com.smartcourier.userservice.model.AvailabilityStatus;
import com.smartcourier.userservice.model.CourierProfile;
import com.smartcourier.userservice.model.User;
import com.smartcourier.userservice.repository.CourierProfileRepository;
import com.smartcourier.userservice.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CourierService {

    private final CourierProfileRepository courierProfileRepository;
    private final UserRepository userRepository;

    public CourierService(CourierProfileRepository courierProfileRepository,
                          UserRepository userRepository) {
        this.courierProfileRepository = courierProfileRepository;
        this.userRepository = userRepository;
    }

    /**
     * Return availability info for the authenticated courier.
     *
     * @throws CourierProfileNotFoundException if no profile exists for the userId
     */
    public CourierAvailabilityResponse getAvailability(UUID userId) {
        CourierProfile profile = findProfileByUserId(userId);
        return toAvailabilityResponse(profile);
    }

    /**
     * Update availability status for the authenticated courier.
     * BUSY cannot be set directly — it is managed by the Delivery & Tracking Service.
     *
     * @throws InvalidAvailabilityTransitionException if requested status is BUSY
     * @throws CourierProfileNotFoundException        if no profile found
     */
    @Transactional
    public CourierAvailabilityResponse updateAvailability(UUID userId, UpdateAvailabilityRequest request) {
        if (request.getAvailabilityStatus() == AvailabilityStatus.BUSY) {
            throw new InvalidAvailabilityTransitionException(
                    "'BUSY' is set automatically by the Delivery & Tracking Service when a booking is assigned. " +
                    "Only 'AVAILABLE' and 'OFFLINE' can be set by the courier directly.");
        }

        CourierProfile profile = findProfileByUserId(userId);
        profile.setAvailabilityStatus(request.getAvailabilityStatus());
        profile = courierProfileRepository.save(profile);

        return toAvailabilityResponse(profile);
    }

    /**
     * Return all couriers whose availabilityStatus is AVAILABLE.
     * Used by the internal API endpoint consumed by other microservices.
     */
    public List<InternalCourierResponse> getAvailableCouriers() {
        return courierProfileRepository
                .findByAvailabilityStatus(AvailabilityStatus.AVAILABLE)
                .stream()
                .map(this::toInternalCourierResponse)
                .collect(Collectors.toList());
    }

    /**
     * Return minimal customer info for inter-service calls.
     *
     * @throws UserNotFoundException if the customer is not found
     */
    public InternalCustomerResponse getCustomerById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("Customer not found with id: " + id));

        return InternalCustomerResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .phoneNumber(user.getPhoneNumber())
                .accountStatus(user.getAccountStatus())
                .build();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private CourierProfile findProfileByUserId(UUID userId) {
        return courierProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new CourierProfileNotFoundException(
                        "Courier profile not found for user id: " + userId));
    }

    private CourierAvailabilityResponse toAvailabilityResponse(CourierProfile profile) {
        return CourierAvailabilityResponse.builder()
                .userId(profile.getUser().getId())
                .vehicleType(profile.getVehicleType())
                .vehicleNumber(profile.getVehicleNumber())
                .availabilityStatus(profile.getAvailabilityStatus())
                .activeBookingCount(profile.getActiveBookingCount())
                .build();
    }

    private InternalCourierResponse toInternalCourierResponse(CourierProfile profile) {
        return InternalCourierResponse.builder()
                .userId(profile.getUser().getId())
                .fullName(profile.getUser().getFullName())
                .vehicleType(profile.getVehicleType())
                .vehicleNumber(profile.getVehicleNumber())
                .build();
    }
}
