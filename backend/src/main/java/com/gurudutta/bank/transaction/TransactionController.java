package com.gurudutta.bank.transaction;

import com.gurudutta.bank.common.dto.PageResponse;
import com.gurudutta.bank.transaction.dto.DashboardResponse;
import com.gurudutta.bank.transaction.dto.DepositRequest;
import com.gurudutta.bank.transaction.dto.TransactionResponse;
import com.gurudutta.bank.transaction.dto.TransferRequest;
import com.gurudutta.bank.transaction.dto.TransferResponse;
import com.gurudutta.bank.transaction.dto.WithdrawRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/transactions")
@Tag(name = "Transactions", description = "Deposits, withdrawals, transfers and statement history")
public class TransactionController {

    // Goes through the facade, not TransactionService directly: the facade adds the retry-on-lock
    // -contention policy, which has to live outside the transaction boundary to be effective.
    private final MoneyMovementFacade moneyMovement;
    private final TransactionQueryService queryService;

    public TransactionController(MoneyMovementFacade moneyMovement,
                                 TransactionQueryService queryService) {
        this.moneyMovement = moneyMovement;
        this.queryService = queryService;
    }

    @PostMapping("/deposit")
    @Operation(summary = "Credit an account")
    public ResponseEntity<TransactionResponse> deposit(@Valid @RequestBody DepositRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(moneyMovement.deposit(request));
    }

    @PostMapping("/withdraw")
    @Operation(summary = "Debit an account")
    public ResponseEntity<TransactionResponse> withdraw(@Valid @RequestBody WithdrawRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(moneyMovement.withdraw(request));
    }

    @PostMapping("/transfer")
    @Operation(summary = "Move money between two accounts atomically")
    public ResponseEntity<TransferResponse> transfer(@Valid @RequestBody TransferRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(moneyMovement.transfer(request));
    }

    @GetMapping
    @Operation(summary = "Paged, filtered transaction history for the caller's accounts")
    public ResponseEntity<PageResponse<TransactionResponse>> search(
            @RequestParam(required = false) String accountNumber,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {

        return ResponseEntity.ok(
                queryService.search(accountNumber, type, from, to, minAmount, search, page, size));
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Aggregated balances, recent activity and chart data for the home screen")
    public ResponseEntity<DashboardResponse> dashboard() {
        return ResponseEntity.ok(queryService.dashboard());
    }
}
