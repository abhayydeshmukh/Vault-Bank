package com.vaultbank.exception;

public class InsufficientBalanceException extends VaultBankException {
    public InsufficientBalanceException(String message) {
        super(message);
    }
}
