package com.vaultbank.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {
    private Long id;
    private String transactionReference;
    private String transactionType;
    private BigDecimal amount;
    private String description;
    private String sourceAccountNumber;
    private String destinationAccountNumber;
    private LocalDateTime createdAt;
}
