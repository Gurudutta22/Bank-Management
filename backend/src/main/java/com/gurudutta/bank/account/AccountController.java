package com.gurudutta.bank.account;

import com.gurudutta.bank.account.dto.AccountResponse;
import com.gurudutta.bank.account.dto.OpenAccountRequest;
import com.gurudutta.bank.transaction.TransactionQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Accounts", description = "Open, list, inspect and close bank accounts")
public class AccountController {

    private final AccountService accountService;
    private final TransactionQueryService transactionQueryService;

    public AccountController(AccountService accountService,
                             TransactionQueryService transactionQueryService) {
        this.accountService = accountService;
        this.transactionQueryService = transactionQueryService;
    }

    @GetMapping
    @Operation(summary = "List every account belonging to the authenticated customer")
    public ResponseEntity<List<AccountResponse>> myAccounts() {
        return ResponseEntity.ok(accountService.myAccounts());
    }

    @PostMapping
    @Operation(summary = "Open a new account")
    public ResponseEntity<AccountResponse> open(@Valid @RequestBody OpenAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.openAccount(request));
    }

    @GetMapping("/{accountNumber}")
    @Operation(summary = "Fetch a single account by number")
    public ResponseEntity<AccountResponse> get(@PathVariable String accountNumber) {
        return ResponseEntity.ok(accountService.getAccount(accountNumber));
    }

    @DeleteMapping("/{accountNumber}")
    @Operation(summary = "Close an account (balance must be zero)")
    public ResponseEntity<AccountResponse> close(@PathVariable String accountNumber) {
        return ResponseEntity.ok(accountService.closeAccount(accountNumber));
    }

    /**
     * Streams the statement as CSV.
     *
     * <p>{@code Content-Disposition: attachment} is what makes the browser download the file
     * instead of rendering it, and the filename is what the customer sees on disk.
     */
    @GetMapping(value = "/{accountNumber}/statement", produces = "text/csv")
    @Operation(summary = "Download a date-ranged account statement as CSV")
    public ResponseEntity<byte[]> statement(
            @PathVariable String accountNumber,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        String csv = transactionQueryService.statementCsv(accountNumber, from, to);
        String filename = "statement-%s-%s-to-%s.csv".formatted(accountNumber, from, to);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
