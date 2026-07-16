package com.smartcourier.userservice.dto;

import com.smartcourier.userservice.model.Role;
import com.smartcourier.userservice.model.VehicleType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class RegisterRequest {

    @NotBlank
    @Size(max = 150)
    private String fullName;

    @NotBlank
    @Email
    @Size(max = 150)
    private String email;

    @NotBlank
    @Size(max = 20)
    private String phoneNumber;

    @NotBlank
    @Size(min = 8, max = 100)
    private String password;

    @NotNull
    private Role role;

    // Nullable — conditional requirement (vehicleType required for COURIER role)
    // is enforced in the service layer, not here.
    private VehicleType vehicleType;

    @Size(max = 20)
    private String vehicleNumber;
}
