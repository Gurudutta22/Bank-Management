package com.gurudutta.bank.transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    List<Transaction> findByReferenceOrderByIdAsc(String reference);

    /**
     * The statement query. Every filter is optional, so one query backs the whole transactions
     * screen instead of a combinatorial explosion of finder methods.
     *
     * <p>{@code JOIN FETCH} on account/owner avoids the N+1 problem: without it, rendering a
     * 25-row page would fire 25 extra SELECTs to resolve each row's account.
     */
    @Query(value = """
            SELECT t FROM Transaction t
            JOIN FETCH t.account a
            JOIN FETCH a.owner
            LEFT JOIN FETCH t.counterpartyAccount
            WHERE a.id IN :accountIds
              AND (:type   IS NULL OR t.type = :type)
              AND (:from   IS NULL OR t.createdAt >= :from)
              AND (:to     IS NULL OR t.createdAt <= :to)
              AND (:minAmount IS NULL OR t.amount >= :minAmount)
              AND (:search IS NULL OR :search = ''
                   OR LOWER(t.description) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR t.reference LIKE CONCAT('%', :search, '%'))
            """,
            countQuery = """
                    SELECT COUNT(t) FROM Transaction t
                    WHERE t.account.id IN :accountIds
                      AND (:type   IS NULL OR t.type = :type)
                      AND (:from   IS NULL OR t.createdAt >= :from)
                      AND (:to     IS NULL OR t.createdAt <= :to)
                      AND (:minAmount IS NULL OR t.amount >= :minAmount)
                      AND (:search IS NULL OR :search = ''
                           OR LOWER(t.description) LIKE LOWER(CONCAT('%', :search, '%'))
                           OR t.reference LIKE CONCAT('%', :search, '%'))
                    """)
    Page<Transaction> search(@Param("accountIds") List<Long> accountIds,
                             @Param("type") TransactionType type,
                             @Param("from") Instant from,
                             @Param("to") Instant to,
                             @Param("minAmount") BigDecimal minAmount,
                             @Param("search") String search,
                             Pageable pageable);

    @Query("""
            SELECT t FROM Transaction t
            JOIN FETCH t.account a
            LEFT JOIN FETCH t.counterpartyAccount
            WHERE a.accountNumber = :accountNumber
              AND t.createdAt BETWEEN :from AND :to
            ORDER BY t.createdAt ASC, t.id ASC
            """)
    List<Transaction> findForStatement(@Param("accountNumber") String accountNumber,
                                       @Param("from") Instant from,
                                       @Param("to") Instant to);

    /**
     * Total already debited from an account today. Backs the daily transfer limit check.
     */
    @Query("""
            SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
            WHERE t.account.id = :accountId
              AND t.direction = com.gurudutta.bank.transaction.TransactionDirection.DEBIT
              AND t.status = com.gurudutta.bank.transaction.TransactionStatus.SUCCESS
              AND t.createdAt >= :since
            """)
    BigDecimal sumDebitsSince(@Param("accountId") Long accountId, @Param("since") Instant since);

    /** Monthly credit/debit totals for the dashboard chart, aggregated in the database. */
    @Query("""
            SELECT t.direction, SUM(t.amount)
            FROM Transaction t
            WHERE t.account.id IN :accountIds AND t.createdAt >= :since
            GROUP BY t.direction
            """)
    List<Object[]> sumByDirectionSince(@Param("accountIds") List<Long> accountIds,
                                       @Param("since") Instant since);

    /** Spend grouped by category, for the doughnut chart on the dashboard. */
    @Query("""
            SELECT COALESCE(t.category, 'Other'), SUM(t.amount)
            FROM Transaction t
            WHERE t.account.id IN :accountIds
              AND t.direction = com.gurudutta.bank.transaction.TransactionDirection.DEBIT
              AND t.createdAt >= :since
            GROUP BY COALESCE(t.category, 'Other')
            ORDER BY SUM(t.amount) DESC
            """)
    List<Object[]> sumSpendByCategorySince(@Param("accountIds") List<Long> accountIds,
                                           @Param("since") Instant since);

    @Query("""
            SELECT t FROM Transaction t
            JOIN FETCH t.account a
            JOIN FETCH a.owner
            WHERE a.id IN :accountIds
            ORDER BY t.createdAt DESC, t.id DESC
            """)
    List<Transaction> findRecent(@Param("accountIds") List<Long> accountIds, Pageable pageable);

    /**
     * Raw {createdAt, direction, amount} tuples for a window, used to build the 6-month trend.
     *
     * <p>Grouping by month in JPQL would need a database-specific date function, so the window is
     * fetched once and bucketed in Java. One query, not one per month.
     */
    @Query("""
            SELECT t.createdAt, t.direction, t.amount FROM Transaction t
            WHERE t.account.id IN :accountIds
              AND t.createdAt >= :from AND t.createdAt < :to
            """)
    List<Object[]> findAmountsInRange(@Param("accountIds") List<Long> accountIds,
                                      @Param("from") Instant from,
                                      @Param("to") Instant to);

    long countByCreatedAtAfter(Instant instant);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.createdAt >= :since")
    BigDecimal totalVolumeSince(@Param("since") Instant since);

    /** Daily totals for the admin dashboard trend line. */
    @Query("""
            SELECT CAST(t.createdAt AS java.time.LocalDate), t.direction, SUM(t.amount), COUNT(t)
            FROM Transaction t
            WHERE t.createdAt >= :since
            GROUP BY CAST(t.createdAt AS java.time.LocalDate), t.direction
            ORDER BY CAST(t.createdAt AS java.time.LocalDate)
            """)
    List<Object[]> dailyVolumeSince(@Param("since") Instant since);
}
