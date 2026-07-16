package com.smartcourier.userservice.controller;

import com.smartcourier.userservice.dto.UpdateProfileRequest;
import com.smartcourier.userservice.dto.UserProfileResponse;
import com.smartcourier.userservice.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "User Profile", description = "Manage the authenticated user's own profile")
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Get the authenticated user's profile")
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMyProfile(Authentication authentication) {
        UUID userId = extractUserId(authentication);
        return ResponseEntity.ok(userService.getProfile(userId));
    }

    @Operation(summary = "Update the authenticated user's fullName and phoneNumber")
    @PutMapping("/me")
    public ResponseEntity<UserProfileResponse> updateMyProfile(@Valid @RequestBody UpdateProfileRequest request,
                                                               Authentication authentication) {
        UUID userId = extractUserId(authentication);
        return ResponseEntity.ok(userService.updateProfile(userId, request));
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Extract the UUID principal set by JwtAuthenticationFilter.
     * The principal is stored as the userId string.
     */
    private UUID extractUserId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
