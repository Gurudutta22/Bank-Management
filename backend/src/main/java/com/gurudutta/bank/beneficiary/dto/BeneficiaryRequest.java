package com.gurudutta.bank.beneficiary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BeneficiaryRequest(

        @NotBlank(message = "Account number is required")
        @Pattern(regexp = "^[0-9]{12}$", message = "Account number must be 12 digits")
        String accountNumber,

        @NotBlank(message = "Nickname is required")
        @Size(min = 2, max = 120)
        String nickname,

        boolean favourite) {
}
