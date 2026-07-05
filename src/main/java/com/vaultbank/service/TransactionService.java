package com.vaultbank.service;

import com.vaultbank.dto.request.DepositRequest;
import com.vaultbank.dto.request.TransferRequest;
import com.vaultbank.dto.request.WithdrawRequest;
import com.vaultbank.dto.response.TransactionResponse;

import java.util.List;

public interface TransactionService {
    TransactionResponse deposit(DepositRequest request);
    TransactionResponse withdraw(String email, WithdrawRequest request);
    TransactionResponse transfer(String email, TransferRequest request);
    List<TransactionResponse> getTransactionHistory(String email, String accountNumber);
}
