package com.vaultbank.service;

import com.vaultbank.dto.event.AccountCreatedEvent;
import com.vaultbank.dto.event.DepositEvent;
import com.vaultbank.dto.event.TransferEvent;
import com.vaultbank.dto.event.WithdrawalEvent;

public interface KafkaProducerService {
    void sendAccountCreatedEvent(AccountCreatedEvent event);
    void sendDepositEvent(DepositEvent event);
    void sendWithdrawalEvent(WithdrawalEvent event);
    void sendTransferEvent(TransferEvent event);
}
