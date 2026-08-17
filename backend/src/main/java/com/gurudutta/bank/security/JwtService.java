package com.gurudutta.bank.security;

import com.gurudutta.bank.config.AppProperties;
import com.gurudutta.bank.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Issues and verifies the JSON Web Tokens that carry a session.
 *
 * <p>Two token types are issued at login:
 * <ul>
 *   <li><b>access</b> - short lived (15 min). Sent on every request. If it leaks, the window of
 *       abuse is small.</li>
 *   <li><b>refresh</b> - long lived (7 days), stored server-side so it can actually be revoked.
 *       Exchanged for a new access token when the old one expires.</li>
 * </ul>
 * That split is the standard answer to "long sessions are convenient but stolen tokens are
 * dangerous": the convenient token is the revocable one, the frequently-sent token is disposable.
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_NAME = "name";
    private static final String CLAIM_UID = "uid";
    private static final String CLAIM_TYPE = "typ";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey signingKey;
    private final AppProperties props;

    public JwtService(AppProperties props) {
        this.props = props;
        byte[] keyBytes = props.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            // HS256 requires >= 256 bits of key material; failing loudly beats signing weakly.
            throw new IllegalStateException(
                    "app.jwt.secret must be at least 32 characters (256 bits) for HS256");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(User user) {
        return build(user, TYPE_ACCESS, Duration.ofMinutes(props.jwt().accessTokenMinutes()));
    }

    public String generateRefreshToken(User user) {
        return build(user, TYPE_REFRESH, Duration.ofDays(props.jwt().refreshTokenDays()));
    }

    private String build(User user, String type, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getEmail())
                .id(UUID.randomUUID().toString())
                .issuer(props.jwt().issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .claim(CLAIM_ROLE, user.getRole().name())
                .claim(CLAIM_NAME, user.getFullName())
                .claim(CLAIM_UID, user.getPublicId())
                .claim(CLAIM_TYPE, type)
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /** Returns the claims if the signature and expiry check out, or null if the token is unusable. */
    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(props.jwt().issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Rejected JWT: {}", ex.getMessage());
            return null;
        }
    }

    public boolean isAccessToken(Claims claims) {
        return claims != null && TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class));
    }

    public boolean isRefreshToken(Claims claims) {
        return claims != null && TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class));
    }

    public long accessTokenSeconds() {
        return Duration.ofMinutes(props.jwt().accessTokenMinutes()).toSeconds();
    }

    public Instant refreshTokenExpiry() {
        return Instant.now().plus(Duration.ofDays(props.jwt().refreshTokenDays()));
    }
}
