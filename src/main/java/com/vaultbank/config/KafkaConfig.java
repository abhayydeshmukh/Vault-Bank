package com.vaultbank.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka configuration class that automatically provisions required topics on startup.
 */
@Configuration
public class KafkaConfig {

    public static final String ACCOUNT_CREATED_TOPIC = "vault-bank.account-created";
    public static final String MONEY_DEPOSITED_TOPIC = "vault-bank.money-deposited";
    public static final String MONEY_WITHDRAWN_TOPIC = "vault-bank.money-withdrawn";
    public static final String MONEY_TRANSFERRED_TOPIC = "vault-bank.money-transferred";

    @Bean
    public NewTopic accountCreatedTopic() {
        return TopicBuilder.name(ACCOUNT_CREATED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic moneyDepositedTopic() {
        return TopicBuilder.name(MONEY_DEPOSITED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic moneyWithdrawnTopic() {
        return TopicBuilder.name(MONEY_WITHDRAWN_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic moneyTransferredTopic() {
        return TopicBuilder.name(MONEY_TRANSFERRED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
