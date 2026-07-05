package com.vaultbank.exception;

public class NegativeAmountException extends VaultBankException {
    public NegativeAmountException(String message) {
        super(message);
    }
}
