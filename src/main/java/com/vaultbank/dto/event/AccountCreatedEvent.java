package com.vaultbank.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountCreatedEvent {
    private String accountNumber;
    private String accountType;
    private String ownerEmail;
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
