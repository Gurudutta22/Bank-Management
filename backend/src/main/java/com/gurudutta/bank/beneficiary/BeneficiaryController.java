package com.gurudutta.bank.beneficiary;

import com.gurudutta.bank.beneficiary.dto.BeneficiaryRequest;
import com.gurudutta.bank.beneficiary.dto.BeneficiaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/beneficiaries")
@Tag(name = "Beneficiaries", description = "Saved payees for quick transfers")
public class BeneficiaryController {

    private final BeneficiaryService beneficiaryService;

    public BeneficiaryController(BeneficiaryService beneficiaryService) {
        this.beneficiaryService = beneficiaryService;
    }

    @GetMapping
    @Operation(summary = "List saved payees, favourites first")
    public ResponseEntity<List<BeneficiaryResponse>> list() {
        return ResponseEntity.ok(beneficiaryService.list());
    }

    @PostMapping
    @Operation(summary = "Save a new payee after verifying the account exists")
    public ResponseEntity<BeneficiaryResponse> add(@Valid @RequestBody BeneficiaryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(beneficiaryService.add(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Rename a payee or toggle its favourite flag")
    public ResponseEntity<BeneficiaryResponse> update(@PathVariable Long id,
                                                      @Valid @RequestBody BeneficiaryRequest request) {
        return ResponseEntity.ok(beneficiaryService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remove a saved payee")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        beneficiaryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
