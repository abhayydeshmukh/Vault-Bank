package com.vaultbank.exception;

/**
 * Base exception for all business violations within Vault Bank.
 */
public class VaultBankException extends RuntimeException {
    public VaultBankException(String message) {
        super(message);
    }
}
