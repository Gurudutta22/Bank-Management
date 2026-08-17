package com.gurudutta.bank.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Turns a {@code Authorization: Bearer <jwt>} header into an authenticated SecurityContext.
 *
 * <p>Extends {@code OncePerRequestFilter} so the work is not repeated on forwards or async
 * dispatches. If anything about the token is wrong the filter simply does not authenticate and
 * lets the chain continue - the authorization rules then produce a clean 401, rather than the
 * filter throwing.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final AppUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, AppUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            Claims claims = jwtService.parse(token);
            // A refresh token must not be usable as an access token, or the short access-token
            // lifetime would be meaningless.
            if (claims != null && jwtService.isAccessToken(claims)) {
                authenticate(request, claims.getSubject());
            }
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, String email) {
        try {
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);
            if (!userDetails.isEnabled()) {
                return;
            }
            var authentication = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (UsernameNotFoundException ex) {
            // Valid signature but the user was deleted since the token was issued: stay anonymous.
            SecurityContextHolder.clearContext();
        }
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            String token = header.substring(PREFIX.length()).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }
}
