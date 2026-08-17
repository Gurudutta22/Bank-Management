package com.gurudutta.bank.admin.dto;

import com.gurudutta.bank.audit.AuditLog;

import java.time.Instant;

public record AuditLogResponse(Long id,
                               String actor,
                               String action,
                               String entityType,
                               String entityId,
                               String detail,
                               String ipAddress,
                               String outcome,
                               Instant createdAt) {

    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(log.getId(), log.getActor(), log.getAction(),
                log.getEntityType(), log.getEntityId(), log.getDetail(),
                log.getIpAddress(), log.getOutcome(), log.getCreatedAt());
    }
}
