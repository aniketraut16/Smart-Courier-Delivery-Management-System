package com.smartcourier.userservice.dto;

import com.smartcourier.userservice.model.VehicleType;
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
public class InternalCourierResponse {

    private UUID userId;
    private String fullName;
    private VehicleType vehicleType;
    private String vehicleNumber;
}
