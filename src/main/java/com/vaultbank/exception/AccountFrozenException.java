package com.vaultbank.exception;

public class AccountFrozenException extends VaultBankException {
    public AccountFrozenException(String message) {
        super(message);
    }
}
