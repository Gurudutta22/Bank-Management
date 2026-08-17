package com.gurudutta.bank.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Append-only record of every security- or money-relevant action.
 *
 * <p>Written on its own transaction ({@code REQUIRES_NEW}) so that a rolled-back business
 * operation still leaves evidence that it was attempted - which is the whole point of an audit
 * trail in a regulated system.
 */
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_created", columnList = "created_at"),
        @Index(name = "idx_audit_actor", columnList = "actor")
})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String actor;

    @Column(nullable = false, length = 60)
    private String action;

    @Column(name = "entity_type", length = 60)
    private String entityType;

    @Column(name = "entity_id", length = 60)
    private String entityId;

    @Column(length = 1000)
    private String detail;

    @Column(name = "ip_address", length = 60)
    private String ipAddress;

    @Column(nullable = false, length = 10)
    private String outcome = "SUCCESS";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected AuditLog() {
        // required by JPA
    }

    public AuditLog(String actor, String action, String entityType, String entityId,
                    String detail, String ipAddress, String outcome) {
        this.actor = actor;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.detail = detail;
        this.ipAddress = ipAddress;
        this.outcome = outcome;
    }

    public Long getId() {
        return id;
    }

    public String getActor() {
        return actor;
    }

    public String getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getDetail() {
        return detail;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getOutcome() {
        return outcome;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
