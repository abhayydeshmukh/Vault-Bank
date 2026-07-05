package com.vaultbank.service;

import com.vaultbank.dto.request.LoginRequest;
import com.vaultbank.dto.request.RegisterRequest;
import com.vaultbank.dto.request.TokenRefreshRequest;
import com.vaultbank.dto.response.AuthResponse;
import com.vaultbank.dto.response.TokenRefreshResponse;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    TokenRefreshResponse refreshToken(TokenRefreshRequest request);
    void logout(String email);
}
