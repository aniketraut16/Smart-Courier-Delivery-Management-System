package com.smartcourier.userservice.dto;

import com.smartcourier.userservice.model.AvailabilityStatus;
import jakarta.validation.constraints.NotNull;
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
public class UpdateAvailabilityRequest {

    @NotNull
    private AvailabilityStatus availabilityStatus;
}
