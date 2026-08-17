package com.gurudutta.bank.user;

import com.gurudutta.bank.user.dto.ChangePasswordRequest;
import com.gurudutta.bank.user.dto.UpdateProfileRequest;
import com.gurudutta.bank.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Profile", description = "Self-service profile and password management")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    @Operation(summary = "Fetch the authenticated user's profile")
    public ResponseEntity<UserResponse> profile() {
        return ResponseEntity.ok(userService.currentProfile());
    }

    @PutMapping("/me")
    @Operation(summary = "Update name, phone and address")
    public ResponseEntity<UserResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(request));
    }

    @PostMapping("/me/password")
    @Operation(summary = "Change password and revoke all active sessions")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(request);
        return ResponseEntity.noContent().build();
    }
}
