package com.gurudutta.bank.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(

        @NotBlank(message = "Full name is required")
        @Size(min = 3, max = 120)
        String fullName,

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^[0-9]{10}$", message = "Phone must be exactly 10 digits")
        String phone,

        @Size(max = 255)
        String address) {
}
