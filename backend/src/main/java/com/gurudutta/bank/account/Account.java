package com.gurudutta.bank.account;

import com.gurudutta.bank.common.domain.BaseEntity;
import com.gurudutta.bank.user.User;
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
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "accounts", indexes = {
        @Index(name = "idx_accounts_number", columnList = "account_number", unique = true),
        @Index(name = "idx_accounts_owner", columnList = "user_id")
})
public class Account extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 12-digit public account number. This, not the PK, is what the API and the UI use. */
    @Column(name = "account_number", nullable = false, unique = true, updatable = false, length = 20)
    private String accountNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User owner;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status = AccountStatus.ACTIVE;

    /**
     * Scale 4 rather than 2: intermediate results such as accrued interest need the extra digits,
     * and money is never held in a binary floating point type.
     */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "daily_transfer_limit", nullable = false, precision = 19, scale = 4)
    private BigDecimal dailyTransferLimit;

    /**
     * Optimistic-lock guard. The transfer path also takes a pessimistic row lock; this is the
     * belt-and-braces check that catches any write path that forgets to.
     */
    @Version
    @Column(nullable = false)
    private long version;

    protected Account() {
        // required by JPA
    }

    public Account(String accountNumber, User owner, AccountType type,
                   BigDecimal openingBalance, BigDecimal dailyTransferLimit) {
        this.accountNumber = accountNumber;
        this.owner = owner;
        this.type = type;
        this.balance = openingBalance;
        this.dailyTransferLimit = dailyTransferLimit;
    }

    /** True when the account may be debited at all. */
    public boolean isDebitable() {
        return status == AccountStatus.ACTIVE;
    }

    /** True when the account may receive money. Frozen accounts can still be credited. */
    public boolean isCreditable() {
        return status == AccountStatus.ACTIVE || status == AccountStatus.FROZEN;
    }

    public void credit(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }

    public void debit(BigDecimal amount) {
        this.balance = this.balance.subtract(amount);
    }

    public Long getId() {
        return id;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public User getOwner() {
        return owner;
    }

    public AccountType getType() {
        return type;
    }

    public void setType(AccountType type) {
        this.type = type;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getDailyTransferLimit() {
        return dailyTransferLimit;
    }

    public void setDailyTransferLimit(BigDecimal dailyTransferLimit) {
        this.dailyTransferLimit = dailyTransferLimit;
    }

    public long getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Account other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
