package com.vaultbank.controller;

import com.vaultbank.dto.response.AccountResponse;
import com.vaultbank.dto.response.ApiResponse;
import com.vaultbank.dto.response.UserResponse;
import com.vaultbank.service.AccountService;
import com.vaultbank.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Operations", description = "Endpoints for administrative management of users and accounts")
public class AdminController {

    private final AccountService accountService;
    private final UserService userService;

    public AdminController(AccountService accountService, UserService userService) {
        this.accountService = accountService;
        this.userService = userService;
    }

    @GetMapping("/users")
    @Operation(summary = "View all users (Paginated)", description = "Retrieves a paginated list of all users registered in the system.")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        List<UserResponse> users = userService.getAllUsers(page, size);
        return ResponseEntity.ok(ApiResponse.success("Users retrieved successfully", users));
    }

    @PostMapping("/accounts/{accountNumber}/freeze")
    @Operation(summary = "Freeze a bank account", description = "Locks a bank account, preventing any credit or debit transactions.")
    public ResponseEntity<ApiResponse<AccountResponse>> freezeAccount(@PathVariable String accountNumber) {
        AccountResponse response = accountService.freezeAccount(accountNumber);
        return ResponseEntity.ok(ApiResponse.success("Account frozen successfully", response));
    }

    @PostMapping("/accounts/{accountNumber}/unfreeze")
    @Operation(summary = "Unfreeze a bank account", description = "Re-activates a frozen bank account, permitting transactions again.")
    public ResponseEntity<ApiResponse<AccountResponse>> unfreezeAccount(@PathVariable String accountNumber) {
        AccountResponse response = accountService.unfreezeAccount(accountNumber);
        return ResponseEntity.ok(ApiResponse.success("Account unfrozen successfully", response));
    }
}
