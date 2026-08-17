package com.gurudutta.bank.admin;

import com.gurudutta.bank.account.AccountStatus;
import com.gurudutta.bank.account.dto.AccountResponse;
import com.gurudutta.bank.admin.dto.AdminStatsResponse;
import com.gurudutta.bank.admin.dto.AuditLogResponse;
import com.gurudutta.bank.admin.dto.StatusUpdateRequest;
import com.gurudutta.bank.common.dto.PageResponse;
import com.gurudutta.bank.user.Role;
import com.gurudutta.bank.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Administration", description = "Back-office dashboard, user and account controls, audit trail")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/stats")
    @Operation(summary = "Aggregate metrics for the admin dashboard")
    public ResponseEntity<AdminStatsResponse> stats() {
        return ResponseEntity.ok(adminService.stats());
    }

    @GetMapping("/users")
    @Operation(summary = "Search and page through all users")
    public ResponseEntity<PageResponse<UserResponse>> users(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Role role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {
        return ResponseEntity.ok(adminService.listUsers(search, role, page, size));
    }

    @PatchMapping("/users/{publicId}/status")
    @Operation(summary = "Enable or disable a customer login")
    public ResponseEntity<UserResponse> updateUserStatus(
            @PathVariable String publicId,
            @Valid @RequestBody StatusUpdateRequest request) {
        return ResponseEntity.ok(adminService.updateUserStatus(publicId, request));
    }

    @PatchMapping("/users/{publicId}/kyc")
    @Operation(summary = "Mark a customer's KYC as verified or revoked")
    public ResponseEntity<UserResponse> updateKyc(@PathVariable String publicId,
                                                  @RequestParam boolean verified) {
        return ResponseEntity.ok(adminService.setKycVerified(publicId, verified));
    }

    @GetMapping("/accounts")
    @Operation(summary = "Search and page through all accounts")
    public ResponseEntity<PageResponse<AccountResponse>> accounts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {
        return ResponseEntity.ok(adminService.listAccounts(search, status, page, size));
    }

    @PatchMapping("/accounts/{accountNumber}/status")
    @Operation(summary = "Freeze, unfreeze or close an account")
    public ResponseEntity<AccountResponse> updateAccountStatus(
            @PathVariable String accountNumber,
            @Valid @RequestBody StatusUpdateRequest request) {
        return ResponseEntity.ok(adminService.updateAccountStatus(accountNumber, request));
    }

    @GetMapping("/audit-logs")
    @Operation(summary = "Page through the immutable audit trail")
    public ResponseEntity<PageResponse<AuditLogResponse>> auditLogs(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminService.auditLogs(search, action, page, size));
    }
}
