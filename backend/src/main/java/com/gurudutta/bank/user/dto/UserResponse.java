package com.gurudutta.bank.user.dto;

import com.gurudutta.bank.user.Role;
import com.gurudutta.bank.user.User;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Outbound view of a user.
 *
 * <p>Note what is absent: {@code passwordHash} and the numeric primary key. Returning the entity
 * directly would serialise both, which is why every endpoint maps to a DTO instead.
 */
public record UserResponse(String id,
                           String fullName,
                           String email,
                           String phone,
                           Role role,
                           boolean enabled,
                           boolean kycVerified,
                           LocalDate dateOfBirth,
                           String address,
                           Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getPublicId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole(),
                user.isEnabled(),
                user.isKycVerified(),
                user.getDateOfBirth(),
                user.getAddress(),
                user.getCreatedAt());
    }
}
