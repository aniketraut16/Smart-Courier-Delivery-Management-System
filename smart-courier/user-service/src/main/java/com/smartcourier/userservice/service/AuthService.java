package com.smartcourier.userservice.service;

import com.smartcourier.userservice.dto.AuthResponse;
import com.smartcourier.userservice.dto.LoginRequest;
import com.smartcourier.userservice.dto.RegisterRequest;
import com.smartcourier.userservice.dto.UserProfileResponse;
import com.smartcourier.userservice.exception.AccountNotActiveException;
import com.smartcourier.userservice.exception.EmailAlreadyExistsException;
import com.smartcourier.userservice.exception.InvalidCredentialsException;
import com.smartcourier.userservice.exception.InvalidRegistrationRoleException;
import com.smartcourier.userservice.exception.MissingCourierDetailsException;
import com.smartcourier.userservice.exception.PhoneAlreadyExistsException;
import com.smartcourier.userservice.model.AccountStatus;
import com.smartcourier.userservice.model.AvailabilityStatus;
import com.smartcourier.userservice.model.CourierProfile;
import com.smartcourier.userservice.model.Role;
import com.smartcourier.userservice.model.User;
import com.smartcourier.userservice.repository.CourierProfileRepository;
import com.smartcourier.userservice.repository.UserRepository;
import com.smartcourier.userservice.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final CourierProfileRepository courierProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       CourierProfileRepository courierProfileRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.courierProfileRepository = courierProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * Register a new CUSTOMER or COURIER account.
     * For COURIER registrations, also creates the associated CourierProfile atomically.
     *
     * @throws InvalidRegistrationRoleException if role == ADMIN
     * @throws MissingCourierDetailsException   if role == COURIER and vehicleType is null
     * @throws EmailAlreadyExistsException      if email is already taken
     * @throws PhoneAlreadyExistsException      if phoneNumber is already taken
     */
    @Transactional
    public UserProfileResponse register(RegisterRequest request) {
        // Business rule: only CUSTOMER and COURIER roles are registerable via API
        if (request.getRole() == Role.ADMIN) {
            throw new InvalidRegistrationRoleException(
                    "Registering as ADMIN is not permitted. ADMIN accounts are provisioned directly in the database.");
        }

        // Business rule: COURIER registration requires vehicleType
        if (request.getRole() == Role.COURIER && request.getVehicleType() == null) {
            throw new MissingCourierDetailsException(
                    "vehicleType is required when registering as a COURIER.");
        }

        // Uniqueness checks
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException(
                    "An account with email '" + request.getEmail() + "' already exists.");
        }
        if (userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new PhoneAlreadyExistsException(
                    "An account with phone number '" + request.getPhoneNumber() + "' already exists.");
        }

        // Persist User
        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .accountStatus(AccountStatus.ACTIVE)
                .build();
        user = userRepository.save(user);

        // If COURIER: create CourierProfile in the same transaction
        if (request.getRole() == Role.COURIER) {
            CourierProfile profile = CourierProfile.builder()
                    .user(user)
                    .vehicleType(request.getVehicleType())
                    .vehicleNumber(request.getVehicleNumber())
                    .availabilityStatus(AvailabilityStatus.OFFLINE)
                    .activeBookingCount(0)
                    .build();
            courierProfileRepository.save(profile);
        }

        return toProfileResponse(user);
    }

    /**
     * Authenticate a user and return a signed JWT.
     *
     * @throws InvalidCredentialsException if email not found or password doesn't match
     * @throws AccountNotActiveException   if accountStatus != ACTIVE
     */
    public AuthResponse login(LoginRequest request) {
        // Same exception/message for both "email not found" and "wrong password" — avoid info leakage
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password."));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password.");
        }

        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException(
                    "Your account is " + user.getAccountStatus().name().toLowerCase() + ". Please contact support.");
        }

        String token = jwtService.generateToken(user);

        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresInMs(86400000L) // reflects jwt.expiration-ms value
                .userId(user.getId())
                .role(user.getRole())
                .fullName(user.getFullName())
                .build();
    }

    // ── Shared mapping helper ─────────────────────────────────────────────────

    public static UserProfileResponse toProfileResponse(User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .role(user.getRole())
                .accountStatus(user.getAccountStatus())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
