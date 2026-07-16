package com.smartcourier.userservice.dto;

import com.smartcourier.userservice.model.AvailabilityStatus;
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
public class CourierAvailabilityResponse {

    private UUID userId;
    private VehicleType vehicleType;
    private String vehicleNumber;
    private AvailabilityStatus availabilityStatus;
    private Integer activeBookingCount;
}
