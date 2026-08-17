package com.gurudutta.bank.beneficiary;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BeneficiaryRepository extends JpaRepository<Beneficiary, Long> {

    List<Beneficiary> findByOwnerIdOrderByFavouriteDescNicknameAsc(Long ownerId);

    Optional<Beneficiary> findByIdAndOwnerId(Long id, Long ownerId);

    boolean existsByOwnerIdAndAccountNumber(Long ownerId, String accountNumber);
}
