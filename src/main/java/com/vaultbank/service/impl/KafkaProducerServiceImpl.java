package com.vaultbank.service.impl;

import com.vaultbank.config.KafkaConfig;
import com.vaultbank.dto.event.AccountCreatedEvent;
import com.vaultbank.dto.event.DepositEvent;
import com.vaultbank.dto.event.TransferEvent;
import com.vaultbank.dto.event.WithdrawalEvent;
import com.vaultbank.service.KafkaProducerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class KafkaProducerServiceImpl implements KafkaProducerService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaProducerServiceImpl(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void sendAccountCreatedEvent(AccountCreatedEvent event) {
        log.info("Publishing AccountCreatedEvent to Kafka: {}", event);
        kafkaTemplate.send(KafkaConfig.ACCOUNT_CREATED_TOPIC, event.getAccountNumber(), event);
    }

    @Override
    public void sendDepositEvent(DepositEvent event) {
        log.info("Publishing DepositEvent to Kafka: {}", event);
        kafkaTemplate.send(KafkaConfig.MONEY_DEPOSITED_TOPIC, event.getAccountNumber(), event);
    }

    @Override
    public void sendWithdrawalEvent(WithdrawalEvent event) {
        log.info("Publishing WithdrawalEvent to Kafka: {}", event);
        kafkaTemplate.send(KafkaConfig.MONEY_WITHDRAWN_TOPIC, event.getAccountNumber(), event);
    }

    @Override
    public void sendTransferEvent(TransferEvent event) {
        log.info("Publishing TransferEvent to Kafka: {}", event);
        kafkaTemplate.send(KafkaConfig.MONEY_TRANSFERRED_TOPIC, event.getSourceAccountNumber(), event);
    }
}
