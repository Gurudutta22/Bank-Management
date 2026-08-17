package com.gurudutta.bank.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Records an action on an independent transaction.
     *
     * <p>{@code REQUIRES_NEW} matters: if a transfer fails validation and its transaction rolls
     * back, the audit row must survive. A failed attempt is exactly the thing a compliance officer
     * wants to see.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String entityType, String entityId, String detail, String outcome) {
        try {
            auditLogRepository.save(new AuditLog(
                    currentActor(), action, entityType, entityId, detail, currentIp(), outcome));
        } catch (Exception ex) {
            // Auditing must never be the reason a customer's transfer fails.
            log.error("Failed to write audit log for action={} entity={}", action, entityId, ex);
        }
    }

    public void success(String action, String entityType, String entityId, String detail) {
        record(action, entityType, entityId, detail, "SUCCESS");
    }

    public void failure(String action, String entityType, String entityId, String detail) {
        record(action, entityType, entityId, detail, "FAILURE");
    }

    private String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return "anonymous";
        }
        return auth.getName();
    }

    private String currentIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return "n/a";
        }
        HttpServletRequest request = attrs.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
