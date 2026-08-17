package com.gurudutta.bank.user;

import com.gurudutta.bank.audit.AuditService;
import com.gurudutta.bank.auth.RefreshTokenRepository;
import com.gurudutta.bank.common.exception.DuplicateResourceException;
import com.gurudutta.bank.common.exception.InvalidOperationException;
import com.gurudutta.bank.security.SecurityUtils;
import com.gurudutta.bank.user.dto.ChangePasswordRequest;
import com.gurudutta.bank.user.dto.UpdateProfileRequest;
import com.gurudutta.bank.user.dto.UserResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public UserResponse currentProfile() {
        return UserResponse.from(SecurityUtils.currentUser());
    }

    @Transactional
    public UserResponse updateProfile(UpdateProfileRequest request) {
        User user = reload(SecurityUtils.currentUser().getId());

        if (!user.getPhone().equals(request.phone())
                && userRepository.existsByPhone(request.phone())) {
            throw new DuplicateResourceException("That phone number is already registered.");
        }

        user.setFullName(request.fullName().trim());
        user.setPhone(request.phone());
        user.setAddress(request.address());

        auditService.success("PROFILE_UPDATED", "User", user.getPublicId(), "Profile details changed");
        return UserResponse.from(user);
    }

    /**
     * Changes the password and invalidates every existing session.
     *
     * <p>Revoking the refresh tokens is the point of the exercise: if the password was changed
     * because it was compromised, leaving the attacker's session alive would defeat the change.
     */
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        User user = reload(SecurityUtils.currentUser().getId());

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            auditService.failure("PASSWORD_CHANGE", "User", user.getPublicId(),
                    "Incorrect current password supplied");
            throw new InvalidOperationException("Your current password is incorrect.",
                    "INCORRECT_PASSWORD");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new InvalidOperationException("The new password must be different from the old one.",
                    "PASSWORD_REUSED");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        refreshTokenRepository.revokeAllForUser(user.getId());

        auditService.success("PASSWORD_CHANGED", "User", user.getPublicId(),
                "Password changed; all sessions revoked");
    }

    private User reload(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new InvalidOperationException("Your account no longer exists."));
    }
}
