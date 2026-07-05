package com.vaultbank.controller;

import com.vaultbank.dto.request.DepositRequest;
import com.vaultbank.dto.request.TransferRequest;
import com.vaultbank.dto.request.WithdrawRequest;
import com.vaultbank.dto.response.ApiResponse;
import com.vaultbank.dto.response.TransactionResponse;
import com.vaultbank.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Transactions", description = "Endpoints for deposits, withdrawals, fund transfers, and ledger lookups")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/deposit")
    @Operation(summary = "Deposit money", description = "Credits funds into the specified active bank account.")
    public ResponseEntity<ApiResponse<TransactionResponse>> deposit(@Valid @RequestBody DepositRequest request) {
        TransactionResponse response = transactionService.deposit(request);
        return ResponseEntity.ok(ApiResponse.success("Deposit successful", response));
    }

    @PostMapping("/withdraw")
    @Operation(summary = "Withdraw money", description = "Debits funds from the caller's active bank account. Checks ownership and balance.")
    public ResponseEntity<ApiResponse<TransactionResponse>> withdraw(@Valid @RequestBody WithdrawRequest request,
                                                                     Principal principal) {
        TransactionResponse response = transactionService.withdraw(principal.getName(), request);
        return ResponseEntity.ok(ApiResponse.success("Withdrawal successful", response));
    }

    @PostMapping("/transfer")
    @Operation(summary = "Transfer money", description = "Debits funds from a source account and credits a destination account atomically. Checks ownership of source.")
    public ResponseEntity<ApiResponse<TransactionResponse>> transfer(@Valid @RequestBody TransferRequest request,
                                                                     Principal principal) {
        TransactionResponse response = transactionService.transfer(principal.getName(), request);
        return ResponseEntity.ok(ApiResponse.success("Transfer successful", response));
    }

    @GetMapping("/history/{accountNumber}")
    @Operation(summary = "Get transaction history", description = "Retrieves ledger logs for an account. Accessible by the account owner or an admin.")
    public ResponseEntity<ApiResponse<List<TransactionResponse>>> getTransactionHistory(@PathVariable String accountNumber,
                                                                                        Principal principal) {
        List<TransactionResponse> response = transactionService.getTransactionHistory(principal.getName(), accountNumber);
        return ResponseEntity.ok(ApiResponse.success("Transaction history retrieved successfully", response));
    }
}
