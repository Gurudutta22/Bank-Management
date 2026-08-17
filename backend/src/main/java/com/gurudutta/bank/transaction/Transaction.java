package com.gurudutta.bank.transaction;

import com.gurudutta.bank.account.Account;
import com.gurudutta.bank.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One immutable ledger line.
 *
 * <p>A transfer writes <em>two</em> of these - a DEBIT on the sender and a CREDIT on the receiver -
 * sharing one {@code reference}. That is the double-entry principle: every rupee that leaves one
 * account is recorded arriving in another, so the ledger can always be proved to balance.
 *
 * <p>Rows are never updated or deleted. A mistake is corrected by posting a compensating entry,
 * exactly as a real bank does, which keeps the audit trail intact.
 */
@Entity
@Table(name = "transactions", indexes = {
        @Index(name = "idx_tx_account_created", columnList = "account_id, created_at"),
        @Index(name = "idx_tx_reference", columnList = "reference"),
        @Index(name = "idx_tx_idempotency", columnList = "idempotency_key", unique = true)
})
public class Transaction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Shared by both legs of a transfer, so the pair can be pulled back together. */
    @Column(nullable = false, updatable = false, length = 36)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    /** The other side of the movement. Null for deposits and cash withdrawals. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "counterparty_account_id")
    private Account counterpartyAccount;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionDirection direction;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /**
     * Running balance immediately after this line was posted. Denormalised on purpose: a statement
     * would otherwise have to re-sum the whole account history for every row it renders.
     */
    @Column(name = "balance_after", nullable = false, precision = 19, scale = 4)
    private BigDecimal balanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionStatus status = TransactionStatus.SUCCESS;

    @Column(length = 255)
    private String description;

    @Column(length = 60)
    private String category;

    /**
     * Client-supplied de-duplication token. The unique index on this column is what makes a retried
     * or double-clicked transfer safe: the second insert fails instead of moving the money twice.
     */
    @Column(name = "idempotency_key", length = 80)
    private String idempotencyKey;

    protected Transaction() {
        // required by JPA
    }

    public Transaction(String reference, Account account, Account counterpartyAccount,
                       TransactionType type, TransactionDirection direction, BigDecimal amount,
                       BigDecimal balanceAfter, String description, String category) {
        this.reference = reference;
        this.account = account;
        this.counterpartyAccount = counterpartyAccount;
        this.type = type;
        this.direction = direction;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.description = description;
        this.category = category;
    }

    /** Signed amount: positive for credits, negative for debits. Used by analytics and CSV export. */
    public BigDecimal signedAmount() {
        return direction == TransactionDirection.CREDIT ? amount : amount.negate();
    }

    public Long getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public Account getAccount() {
        return account;
    }

    public Account getCounterpartyAccount() {
        return counterpartyAccount;
    }

    public TransactionType getType() {
        return type;
    }

    public TransactionDirection getDirection() {
        return direction;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Transaction other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
