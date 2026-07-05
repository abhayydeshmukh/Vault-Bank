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
 * Consumer representing the Notification dispatch service. Sends SMS/Email alerts for customer transactions.
 */
@Slf4j
@Component
public class NotificationConsumer {

    @KafkaListener(topics = KafkaConfig.ACCOUNT_CREATED_TOPIC, groupId = "vault-bank-notification-group")
    public void consumeAccountCreated(AccountCreatedEvent event) {
        log.info("[NOTIFICATION ALERT] Sending Welcome Email to {}: Your account {} has been created successfully. Welcome to Vault Bank!",
                event.getOwnerEmail(), event.getAccountNumber());
    }

    @KafkaListener(topics = KafkaConfig.MONEY_DEPOSITED_TOPIC, groupId = "vault-bank-notification-group")
    public void consumeDeposit(DepositEvent event) {
        log.info("[NOTIFICATION ALERT] Sending Alert to owner of {}: Cash deposit of ${} processed successfully. Ref: {}",
                event.getAccountNumber(), event.getAmount(), event.getTransactionReference());
    }

    @KafkaListener(topics = KafkaConfig.MONEY_WITHDRAWN_TOPIC, groupId = "vault-bank-notification-group")
    public void consumeWithdrawal(WithdrawalEvent event) {
        log.info("[NOTIFICATION ALERT] Sending Debit Alert to owner of {}: Cash withdrawal of ${} completed. Ref: {}",
                event.getAccountNumber(), event.getAmount(), event.getTransactionReference());
    }

    @KafkaListener(topics = KafkaConfig.MONEY_TRANSFERRED_TOPIC, groupId = "vault-bank-notification-group")
    public void consumeTransfer(TransferEvent event) {
        log.info("[NOTIFICATION ALERT] Sending Transfer Alert: Account {} debited and account {} credited with ${}. Ref: {}",
                event.getSourceAccountNumber(), event.getDestinationAccountNumber(), event.getAmount(), event.getTransactionReference());
    }
}
