package com.vaultbank.controller;

import com.vaultbank.dto.request.CreateAccountRequest;
import com.vaultbank.dto.response.AccountResponse;
import com.vaultbank.dto.response.ApiResponse;
import com.vaultbank.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Accounts", description = "Endpoints for client-side bank account lifecycle management")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    @Operation(summary = "Create a new bank account", description = "Initializes a Savings or Current account for the authenticated user.")
    public ResponseEntity<ApiResponse<AccountResponse>> createAccount(@Valid @RequestBody CreateAccountRequest request,
                                                                      Principal principal) {
        AccountResponse response = accountService.createAccount(principal.getName(), request);
        return new ResponseEntity<>(
                ApiResponse.success("Account created successfully", response),
                HttpStatus.CREATED
        );
    }

    @GetMapping("/my")
    @Operation(summary = "List all my accounts", description = "Returns a list of accounts owned by the authenticated user.")
    public ResponseEntity<ApiResponse<List<AccountResponse>>> getMyAccounts(Principal principal) {
        List<AccountResponse> response = accountService.getMyAccounts(principal.getName());
        return ResponseEntity.ok(ApiResponse.success("Accounts retrieved successfully", response));
    }

    @GetMapping("/{accountNumber}")
    @Operation(summary = "Get account details", description = "Retrieves account information. Accessible only by the owner or an admin.")
    public ResponseEntity<ApiResponse<AccountResponse>> getAccount(@PathVariable String accountNumber,
                                                                   Principal principal) {
        AccountResponse response = accountService.getAccount(principal.getName(), accountNumber);
        return ResponseEntity.ok(ApiResponse.success("Account retrieved successfully", response));
    }

    @PostMapping("/{accountNumber}/close")
    @Operation(summary = "Close bank account", description = "Closes an active bank account. Balance must be 0.00.")
    public ResponseEntity<ApiResponse<Void>> closeAccount(@PathVariable String accountNumber,
                                                          Principal principal) {
        accountService.closeAccount(principal.getName(), accountNumber);
        return ResponseEntity.ok(ApiResponse.success("Account closed successfully"));
    }
}
