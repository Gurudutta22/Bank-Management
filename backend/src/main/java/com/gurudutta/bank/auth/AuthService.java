package com.gurudutta.bank.auth;

import com.gurudutta.bank.audit.AuditService;
import com.gurudutta.bank.auth.dto.AuthResponse;
import com.gurudutta.bank.auth.dto.LoginRequest;
import com.gurudutta.bank.auth.dto.RefreshRequest;
import com.gurudutta.bank.auth.dto.RegisterRequest;
import com.gurudutta.bank.common.exception.BusinessException;
import com.gurudutta.bank.common.exception.DuplicateResourceException;
import com.gurudutta.bank.security.JwtService;
import com.gurudutta.bank.user.Role;
import com.gurudutta.bank.user.User;
import com.gurudutta.bank.user.UserRepository;
import com.gurudutta.bank.user.dto.UserResponse;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final AuditService auditService;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.auditService = auditService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("An account with this email already exists.");
        }
        if (userRepository.existsByPhone(request.phone())) {
            throw new DuplicateResourceException("An account with this phone number already exists.");
        }

        User user = new User(
                request.fullName().trim(),
                email,
                request.phone(),
                passwordEncoder.encode(request.password()),
                // Self-registration can never mint an admin. Role escalation is an admin-only action,
                // otherwise anyone could POST {"role":"ADMIN"} and own the bank.
                Role.CUSTOMER);
        user.setDateOfBirth(request.dateOfBirth());
        user.setAddress(request.address());

        User saved = userRepository.save(user);
        auditService.success("USER_REGISTERED", "User", saved.getPublicId(), "Self-registration");
        log.info("Registered new customer {}", saved.getPublicId());

        return issueTokens(saved);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        try {
            // Delegating to the AuthenticationManager means BCrypt comparison, the enabled check and
            // the "unknown user" masking all happen in Spring Security's well-tested code path.
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (org.springframework.security.core.AuthenticationException ex) {
            auditService.failure("LOGIN_FAILED", "User", email, ex.getClass().getSimpleName());
            throw ex;
        }

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BusinessException("Invalid email or password.",
                        HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS"));

        auditService.success("LOGIN", "User", user.getPublicId(), "Password login");
        return issueTokens(user);
    }

    /**
     * Exchanges a refresh token for a fresh pair, rotating the refresh token in the process.
     *
     * <p>Rotation matters: if an attacker steals a refresh token and uses it, the legitimate user's
     * copy is already revoked, so their next refresh fails and the theft becomes visible instead of
     * granting silent, indefinite access.
     */
    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        Claims claims = jwtService.parse(request.refreshToken());
        if (claims == null || !jwtService.isRefreshToken(claims)) {
            throw new BusinessException("Refresh token is invalid or expired.",
                    HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN");
        }

        String hash = sha256(request.refreshToken());
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BusinessException("Refresh token is not recognised.",
                        HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN"));

        if (!stored.isUsable()) {
            throw new BusinessException("Refresh token has been revoked or has expired.",
                    HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN");
        }

        User user = stored.getUser();
        if (!user.isEnabled()) {
            throw new BusinessException("This account has been disabled.",
                    HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED");
        }

        stored.setRevoked(true);
        refreshTokenRepository.save(stored);
        return issueTokens(user);
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(sha256(refreshToken)).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
            auditService.success("LOGOUT", "User", token.getUser().getPublicId(), "Token revoked");
        });
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        refreshTokenRepository.save(
                new RefreshToken(sha256(refreshToken), user, jwtService.refreshTokenExpiry()));

        return AuthResponse.of(accessToken, refreshToken,
                jwtService.accessTokenSeconds(), UserResponse.from(user));
    }

    /**
     * Hashes the token before storage. SHA-256 without a salt is correct here (unlike for
     * passwords): the input is 200+ bits of random signed JWT, so there is nothing to brute-force.
     */
    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required but unavailable", ex);
        }
    }
}
