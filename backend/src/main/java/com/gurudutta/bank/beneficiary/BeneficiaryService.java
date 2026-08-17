package com.gurudutta.bank.beneficiary;

import com.gurudutta.bank.account.Account;
import com.gurudutta.bank.account.AccountRepository;
import com.gurudutta.bank.account.AccountStatus;
import com.gurudutta.bank.audit.AuditService;
import com.gurudutta.bank.beneficiary.dto.BeneficiaryRequest;
import com.gurudutta.bank.beneficiary.dto.BeneficiaryResponse;
import com.gurudutta.bank.common.exception.DuplicateResourceException;
import com.gurudutta.bank.common.exception.InvalidOperationException;
import com.gurudutta.bank.common.exception.ResourceNotFoundException;
import com.gurudutta.bank.security.SecurityUtils;
import com.gurudutta.bank.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BeneficiaryService {

    private final BeneficiaryRepository beneficiaryRepository;
    private final AccountRepository accountRepository;
    private final AuditService auditService;

    public BeneficiaryService(BeneficiaryRepository beneficiaryRepository,
                              AccountRepository accountRepository,
                              AuditService auditService) {
        this.beneficiaryRepository = beneficiaryRepository;
        this.accountRepository = accountRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<BeneficiaryResponse> list() {
        return beneficiaryRepository
                .findByOwnerIdOrderByFavouriteDescNicknameAsc(SecurityUtils.currentUser().getId())
                .stream()
                .map(BeneficiaryResponse::from)
                .toList();
    }

    @Transactional
    public BeneficiaryResponse add(BeneficiaryRequest request) {
        User owner = SecurityUtils.currentUser();

        Account target = accountRepository.findByAccountNumber(request.accountNumber())
                .orElseThrow(() -> new InvalidOperationException(
                        "No NovaBank account found with that number.", "PAYEE_NOT_FOUND"));

        if (target.getStatus() == AccountStatus.CLOSED) {
            throw new InvalidOperationException("That account is closed and cannot be added as a payee.");
        }
        if (target.getOwner().getId().equals(owner.getId())) {
            throw new InvalidOperationException("You cannot add your own account as a beneficiary.",
                    "SELF_BENEFICIARY");
        }
        if (beneficiaryRepository.existsByOwnerIdAndAccountNumber(owner.getId(), request.accountNumber())) {
            throw new DuplicateResourceException("This payee is already saved.");
        }

        Beneficiary beneficiary = new Beneficiary(owner, request.accountNumber(),
                request.nickname().trim(), target.getOwner().getFullName());
        beneficiary.setFavourite(request.favourite());

        Beneficiary saved = beneficiaryRepository.save(beneficiary);
        auditService.success("BENEFICIARY_ADDED", "Beneficiary", request.accountNumber(),
                "Added payee " + request.nickname());
        return BeneficiaryResponse.from(saved);
    }

    @Transactional
    public BeneficiaryResponse update(Long id, BeneficiaryRequest request) {
        Beneficiary beneficiary = requireOwned(id);
        beneficiary.setNickname(request.nickname().trim());
        beneficiary.setFavourite(request.favourite());
        return BeneficiaryResponse.from(beneficiary);
    }

    @Transactional
    public void delete(Long id) {
        Beneficiary beneficiary = requireOwned(id);
        beneficiaryRepository.delete(beneficiary);
        auditService.success("BENEFICIARY_REMOVED", "Beneficiary",
                beneficiary.getAccountNumber(), "Removed payee " + beneficiary.getNickname());
    }

    /**
     * Scoping the lookup by owner id (rather than fetching by id then checking) means a
     * beneficiary belonging to someone else is indistinguishable from one that does not exist.
     */
    private Beneficiary requireOwned(Long id) {
        return beneficiaryRepository
                .findByIdAndOwnerId(id, SecurityUtils.currentUser().getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Beneficiary", id));
    }
}
