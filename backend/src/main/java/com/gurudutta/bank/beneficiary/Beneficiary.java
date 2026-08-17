package com.gurudutta.bank.beneficiary;

import com.gurudutta.bank.common.domain.BaseEntity;
import com.gurudutta.bank.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.Objects;

/** A saved payee, so a customer does not retype an account number on every transfer. */
@Entity
@Table(name = "beneficiaries",
        uniqueConstraints = @UniqueConstraint(name = "uk_beneficiary_owner_account",
                columnNames = {"owner_id", "account_number"}),
        indexes = @Index(name = "idx_beneficiary_owner", columnList = "owner_id"))
public class Beneficiary extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "account_number", nullable = false, length = 20)
    private String accountNumber;

    @Column(nullable = false, length = 120)
    private String nickname;

    /** Snapshot of the payee's real name at the time it was added, shown for confirmation. */
    @Column(name = "holder_name", nullable = false, length = 120)
    private String holderName;

    @Column(name = "bank_name", length = 120)
    private String bankName = "NovaBank";

    @Column(name = "is_favourite", nullable = false)
    private boolean favourite = false;

    protected Beneficiary() {
        // required by JPA
    }

    public Beneficiary(User owner, String accountNumber, String nickname, String holderName) {
        this.owner = owner;
        this.accountNumber = accountNumber;
        this.nickname = nickname;
        this.holderName = holderName;
    }

    public Long getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getHolderName() {
        return holderName;
    }

    public void setHolderName(String holderName) {
        this.holderName = holderName;
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public boolean isFavourite() {
        return favourite;
    }

    public void setFavourite(boolean favourite) {
        this.favourite = favourite;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Beneficiary other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
