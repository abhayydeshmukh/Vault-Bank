package com.vaultbank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class VaultBankApplication {
    public static void main(String[] args) {
        SpringApplication.run(VaultBankApplication.class, args);
    }
}
