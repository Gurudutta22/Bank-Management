package com.gurudutta.bank.security;

import com.gurudutta.bank.common.exception.BusinessException;
import com.gurudutta.bank.user.Role;
import com.gurudutta.bank.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Convenience access to the authenticated principal. */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppUserPrincipal principal) {
            return principal.getUser();
        }
        throw new BusinessException("Not authenticated.", HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
    }

    public static boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> ("ROLE_" + Role.ADMIN.name()).equals(a.getAuthority()));
    }
}
