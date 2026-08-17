package com.gurudutta.bank.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record RegisterRequest(

        @NotBlank(message = "Full name is required")
        @Size(min = 3, max = 120, message = "Full name must be 3-120 characters")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        @Size(max = 160)
        String email,

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^[0-9]{10}$", message = "Phone must be exactly 10 digits")
        String phone,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be 8-72 characters")
        // Enforced server-side, because client-side validation is a convenience, not a control.
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                message = "Password must contain an uppercase letter, a lowercase letter and a digit")
        String password,

        @Past(message = "Date of birth must be in the past")
        LocalDate dateOfBirth,

        @Size(max = 255)
        String address) {
}
