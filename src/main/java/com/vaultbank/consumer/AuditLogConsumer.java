package com.vaultbank.consumer;

import com.vaultbank.config.KafkaConfig;
import com.vaultbank.dto.event.AccountCreatedEvent;
import com.vaultbank.dto.event.DepositEvent;
import com.vaultbank.dto.event.TransferEvent;
import com.vaultbank.dto.event.WithdrawalEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumer representing the Audit Logging service. It logs all financial ledger mutations.
 */
@Slf4j
@Component
public class AuditLogConsumer {

    @KafkaListener(topics = KafkaConfig.ACCOUNT_CREATED_TOPIC, groupId = "vault-bank-audit-group")
    public void consumeAccountCreated(AccountCreatedEvent event) {
        log.info("[AUDIT LEDGER] ACCOUNT CREATED - Account: {}, Owner: {}, Time: {}",
                event.getAccountNumber(), event.getOwnerEmail(), event.getCreatedAt());
    }

    @KafkaListener(topics = KafkaConfig.MONEY_DEPOSITED_TOPIC, groupId = "vault-bank-audit-group")
    public void consumeDeposit(DepositEvent event) {
        log.info("[AUDIT LEDGER] DEPOSIT DETECTED - Ref: {}, Account: {}, Amount: {}, Time: {}",
                event.getTransactionReference(), event.getAccountNumber(), event.getAmount(), event.getTimestamp());
    }

    @KafkaListener(topics = KafkaConfig.MONEY_WITHDRAWN_TOPIC, groupId = "vault-bank-audit-group")
    public void consumeWithdrawal(WithdrawalEvent event) {
        log.info("[AUDIT LEDGER] WITHDRAWAL DETECTED - Ref: {}, Account: {}, Amount: {}, Time: {}",
                event.getTransactionReference(), event.getAccountNumber(), event.getAmount(), event.getTimestamp());
    }

    @KafkaListener(topics = KafkaConfig.MONEY_TRANSFERRED_TOPIC, groupId = "vault-bank-audit-group")
    public void consumeTransfer(TransferEvent event) {
        log.info("[AUDIT LEDGER] TRANSFER DETECTED - Ref: {}, Source Account: {}, Destination Account: {}, Amount: {}, Time: {}",
                event.getTransactionReference(), event.getSourceAccountNumber(), event.getDestinationAccountNumber(), event.getAmount(), event.getTimestamp());
    }
}
