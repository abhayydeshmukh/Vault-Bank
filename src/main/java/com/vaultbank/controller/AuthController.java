package com.vaultbank.controller;

import com.vaultbank.dto.request.LoginRequest;
import com.vaultbank.dto.request.RegisterRequest;
import com.vaultbank.dto.request.TokenRefreshRequest;
import com.vaultbank.dto.response.ApiResponse;
import com.vaultbank.dto.response.AuthResponse;
import com.vaultbank.dto.response.TokenRefreshResponse;
import com.vaultbank.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Endpoints for user registration, authentication, token rotation, and logout")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new user", description = "Creates a new customer or admin user and generates initial access/refresh tokens.")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return new ResponseEntity<>(
                ApiResponse.success("User registered successfully", response),
                HttpStatus.CREATED
        );
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user", description = "Authenticates user credentials and returns JWT tokens.")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(
                ApiResponse.success("Authentication successful", response)
        );
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh JWT access token", description = "Rotates current refresh token and returns a new access token.")
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> refresh(@Valid @RequestBody TokenRefreshRequest request) {
        TokenRefreshResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(
                ApiResponse.success("Token refreshed successfully", response)
        );
    }

    @PostMapping("/logout")
    @Operation(
            summary = "Logout user",
            description = "Revokes user refresh tokens, ending the session.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<ApiResponse<Void>> logout(Principal principal) {
        if (principal == null) {
            throw new AccessDeniedException("User must be logged in to execute logout.");
        }
        authService.logout(principal.getName());
        return ResponseEntity.ok(
                ApiResponse.success("Logout successful. Refresh token revoked.")
        );
    }
}
