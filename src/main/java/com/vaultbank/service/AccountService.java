package com.vaultbank.service;

import com.vaultbank.dto.request.CreateAccountRequest;
import com.vaultbank.dto.response.AccountResponse;

import java.util.List;

public interface AccountService {
    AccountResponse createAccount(String email, CreateAccountRequest request);
    AccountResponse getAccount(String email, String accountNumber);
    List<AccountResponse> getMyAccounts(String email);
    void closeAccount(String email, String accountNumber);
    AccountResponse freezeAccount(String accountNumber);
    AccountResponse unfreezeAccount(String accountNumber);
}
