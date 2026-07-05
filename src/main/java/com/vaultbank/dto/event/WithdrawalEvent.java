package com.vaultbank.dto.event;

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
public class WithdrawalEvent {
    private String accountNumber;
    private BigDecimal amount;
    private String transactionReference;
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
