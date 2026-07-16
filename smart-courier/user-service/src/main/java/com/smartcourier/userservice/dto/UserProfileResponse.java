package com.smartcourier.userservice.dto;

import com.smartcourier.userservice.model.AccountStatus;
import com.smartcourier.userservice.model.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfileResponse {

    private UUID id;
    private String fullName;
    private String email;
    private String phoneNumber;
    private Role role;
    private AccountStatus accountStatus;

    /** Matches the OffsetDateTime type declared on the User entity. */
    private OffsetDateTime createdAt;
}
