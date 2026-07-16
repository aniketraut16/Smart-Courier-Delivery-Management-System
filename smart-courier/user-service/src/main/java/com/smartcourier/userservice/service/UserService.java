package com.smartcourier.userservice.service;

import com.smartcourier.userservice.dto.UpdateProfileRequest;
import com.smartcourier.userservice.dto.UserProfileResponse;
import com.smartcourier.userservice.exception.PhoneAlreadyExistsException;
import com.smartcourier.userservice.exception.UserNotFoundException;
import com.smartcourier.userservice.model.User;
import com.smartcourier.userservice.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Return the profile for the authenticated user.
     *
     * @throws UserNotFoundException if the userId from the JWT principal is not found
     */
    public UserProfileResponse getProfile(UUID userId) {
        User user = findUserById(userId);
        return AuthService.toProfileResponse(user);
    }

    /**
     * Update fullName and phoneNumber for the authenticated user.
     * Email is intentionally not updatable via this endpoint.
     *
     * @throws UserNotFoundException      if userId not found
     * @throws PhoneAlreadyExistsException if the new phone number is already taken by another account
     */
    @Transactional
    public UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = findUserById(userId);

        // Validate phone uniqueness only if the value changed
        if (!user.getPhoneNumber().equals(request.getPhoneNumber())
                && userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new PhoneAlreadyExistsException(
                    "Phone number '" + request.getPhoneNumber() + "' is already in use.");
        }

        user.setFullName(request.getFullName());
        user.setPhoneNumber(request.getPhoneNumber());
        user = userRepository.save(user);

        return AuthService.toProfileResponse(user);
    }

    /**
     * Return a paginated list of all users (admin only — enforced by @PreAuthorize in the controller).
     */
    public Page<UserProfileResponse> listAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable)
                .map(AuthService::toProfileResponse);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private User findUserById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));
    }
}
