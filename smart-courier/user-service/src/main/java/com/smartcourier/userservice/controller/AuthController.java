package com.smartcourier.userservice.controller;

import com.smartcourier.userservice.dto.AuthResponse;
import com.smartcourier.userservice.dto.LoginRequest;
import com.smartcourier.userservice.dto.RegisterRequest;
import com.smartcourier.userservice.dto.UserProfileResponse;
import com.smartcourier.userservice.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "User registration and authentication")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Register a new CUSTOMER or COURIER account")
    @PostMapping("/register")
    public ResponseEntity<UserProfileResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserProfileResponse profile = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(profile);
    }

    @Operation(summary = "Authenticate and receive a JWT access token")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse authResponse = authService.login(request);
        return ResponseEntity.ok(authResponse);
    }
}
