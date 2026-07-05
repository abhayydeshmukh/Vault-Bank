package com.vaultbank.exception;

public class DuplicateEmailException extends VaultBankException {
    public DuplicateEmailException(String message) {
        super(message);
    }
}
