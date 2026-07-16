package com.smartcourier.userservice.dto;

import com.smartcourier.userservice.model.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {

    private String accessToken;

    /** Always "Bearer". */
    private String tokenType;

    private long expiresInMs;

    private UUID userId;

    private Role role;

    private String fullName;
}
