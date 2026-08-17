package com.gurudutta.bank.auth;

import com.gurudutta.bank.auth.dto.AuthResponse;
import com.gurudutta.bank.auth.dto.LoginRequest;
import com.gurudutta.bank.auth.dto.RefreshRequest;
import com.gurudutta.bank.auth.dto.RegisterRequest;
import com.gurudutta.bank.user.UserService;
import com.gurudutta.bank.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Registration, login, token refresh and logout")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    public AuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    @PostMapping("/register")
    @SecurityRequirements // public endpoint: do not show a padlock in Swagger UI
    @Operation(summary = "Create a customer account and return a token pair")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Exchange email and password for an access + refresh token pair")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    @Operation(summary = "Exchange a refresh token for a new pair (the old one is revoked)")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the supplied refresh token")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshRequest request) {
        authService.logout(request == null ? null : request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @org.springframework.web.bind.annotation.GetMapping("/me")
    @Operation(summary = "Return the profile of the currently authenticated user")
    public ResponseEntity<UserResponse> me() {
        return ResponseEntity.ok(userService.currentProfile());
    }
}
