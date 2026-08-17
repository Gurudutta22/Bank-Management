package com.gurudutta.bank.account;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountNumber(String accountNumber);

    boolean existsByAccountNumber(String accountNumber);

    List<Account> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    long countByOwnerId(Long ownerId);

    long countByStatus(AccountStatus status);

    /**
     * Reads the row under a database write lock ({@code SELECT ... FOR UPDATE}).
     *
     * <p>This is the heart of the money-movement safety story. Two concurrent withdrawals that both
     * read a balance of 1000 would each believe a 700 debit is affordable and overdraw the account.
     * Serialising the read behind a row lock forces the second transaction to wait and re-read the
     * balance the first one committed.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.accountNumber = :accountNumber")
    Optional<Account> findByAccountNumberForUpdate(@Param("accountNumber") String accountNumber);

    @Query("SELECT COALESCE(SUM(a.balance), 0) FROM Account a WHERE a.status <> 'CLOSED'")
    BigDecimal totalHoldings();

    @Query("""
            SELECT a FROM Account a
            WHERE (:search IS NULL OR :search = ''
                   OR a.accountNumber LIKE CONCAT('%', :search, '%')
                   OR LOWER(a.owner.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(a.owner.email)    LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:status IS NULL OR a.status = :status)
            """)
    Page<Account> search(@Param("search") String search,
                         @Param("status") AccountStatus status,
                         Pageable pageable);

    List<Account> findByTypeAndStatus(AccountType type, AccountStatus status);
}
