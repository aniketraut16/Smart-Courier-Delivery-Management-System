package com.smartcourier.userservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateProfileRequest {

    @NotBlank
    @Size(max = 150)
    private String fullName;

    @NotBlank
    @Size(max = 20)
    private String phoneNumber;

    // Email is intentionally not updatable via this endpoint in Phase 1.
}
