package com.smartcourier.userservice.dto;

import com.smartcourier.userservice.model.AccountStatus;
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
public class InternalCustomerResponse {

    private UUID id;
    private String fullName;
    private String phoneNumber;
    private AccountStatus accountStatus;
}
