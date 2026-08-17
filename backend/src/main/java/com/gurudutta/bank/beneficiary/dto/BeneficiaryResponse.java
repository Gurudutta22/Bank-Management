package com.gurudutta.bank.beneficiary.dto;

import com.gurudutta.bank.beneficiary.Beneficiary;

import java.time.Instant;

public record BeneficiaryResponse(Long id,
                                  String accountNumber,
                                  String maskedAccountNumber,
                                  String nickname,
                                  String holderName,
                                  String bankName,
                                  boolean favourite,
                                  Instant addedAt) {

    public static BeneficiaryResponse from(Beneficiary b) {
        String number = b.getAccountNumber();
        String masked = number.length() > 4 ? "•••• " + number.substring(number.length() - 4) : number;
        return new BeneficiaryResponse(b.getId(), number, masked, b.getNickname(),
                b.getHolderName(), b.getBankName(), b.isFavourite(), b.getCreatedAt());
    }
}
