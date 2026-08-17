package com.gurudutta.bank.admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Used for both "enable/disable a user" and "freeze/close an account". */
public record StatusUpdateRequest(

        @NotNull(message = "Status is required")
        String status,

        @Size(max = 255, message = "Reason must be at most 255 characters")
        String reason) {
}
