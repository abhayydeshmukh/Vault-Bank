package com.vaultbank.service.impl;

import com.vaultbank.dto.request.LoginRequest;
import com.vaultbank.dto.request.RegisterRequest;
import com.vaultbank.dto.request.TokenRefreshRequest;
import com.vaultbank.dto.response.AuthResponse;
import com.vaultbank.dto.response.TokenRefreshResponse;
import com.vaultbank.entity.RefreshToken;
import com.vaultbank.entity.User;
import com.vaultbank.exception.DuplicateEmailException;
import com.vaultbank.exception.TokenRefreshException;
import com.vaultbank.repository.RefreshTokenRepository;
import com.vaultbank.repository.UserRepository;
import com.vaultbank.security.JwtTokenProvider;
import com.vaultbank.service.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AuthenticationManager authenticationManager;

    @Value("${app.jwt.refreshTokenExpirationMs}")
    private long refreshTokenExpirationMs;

    public AuthServiceImpl(UserRepository userRepository,
                           RefreshTokenRepository refreshTokenRepository,
                           PasswordEncoder passwordEncoder,
                           JwtTokenProvider tokenProvider,
                           AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.authenticationManager = authenticationManager;
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.info("Attempting to register user with email: {}", request.getEmail());

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException("Email address is already in use.");
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .role(request.getRole())
                .isActive(true)
                .build();

        User savedUser = userRepository.save(user);
        log.info("User registered successfully: {}", savedUser.getEmail());

        // Generate tokens upon successful registration
        String accessToken = tokenProvider.generateAccessTokenFromEmailAndRole(savedUser.getEmail(), savedUser.getRole().name());
        String refreshTokenStr = createOrUpdateRefreshToken(savedUser);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenStr)
                .email(savedUser.getEmail())
                .role(savedUser.getRole().name())
                .build();
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        log.info("Attempting to authenticate user: {}", request.getEmail());

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        String accessToken = tokenProvider.generateAccessToken(authentication);
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new TokenRefreshException("User record not found post-auth."));

        String refreshTokenStr = createOrUpdateRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenStr)
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }

    @Override
    @Transactional
    public TokenRefreshResponse refreshToken(TokenRefreshRequest request) {
        String requestRefreshTokenStr = request.getRefreshToken();
        log.info("Processing token refresh request");

        RefreshToken refreshToken = refreshTokenRepository.findByToken(requestRefreshTokenStr)
                .orElseThrow(() -> new TokenRefreshException("Refresh token is not in database."));

        // Verify Expiration
        if (refreshToken.getExpiryDate().compareTo(Instant.now()) < 0) {
            refreshTokenRepository.delete(refreshToken);
            throw new TokenRefreshException("Refresh token was expired. Please sign in again.");
        }

        // Token validation via provider (ensures cryptographical integrity)
        if (!tokenProvider.validateToken(requestRefreshTokenStr)) {
            throw new TokenRefreshException("Refresh token cryptographic validation failed.");
        }

        User user = refreshToken.getUser();
        
        // Generate new Access and Refresh tokens (Refresh Token Rotation)
        String newAccessToken = tokenProvider.generateAccessTokenFromEmailAndRole(user.getEmail(), user.getRole().name());
        String newRefreshTokenStr = createOrUpdateRefreshToken(user);

        return TokenRefreshResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshTokenStr)
                .build();
    }

    @Override
    @Transactional
    public void logout(String email) {
        log.info("Logging out user: {}", email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new TokenRefreshException("User not found for logout."));
        refreshTokenRepository.deleteByUser(user);
    }

    private String createOrUpdateRefreshToken(User user) {
        String tokenStr = tokenProvider.generateRefreshToken(user.getEmail());
        Instant expiryDate = Instant.now().plusMillis(refreshTokenExpirationMs);

        // Fetch or create
        RefreshToken refreshToken = user.getRefreshToken();
        if (refreshToken == null) {
            refreshToken = RefreshToken.builder()
                    .user(user)
                    .token(tokenStr)
                    .expiryDate(expiryDate)
                    .build();
            user.setRefreshToken(refreshToken);
        } else {
            refreshToken.setToken(tokenStr);
            refreshToken.setExpiryDate(expiryDate);
        }

        refreshTokenRepository.save(refreshToken);
        return tokenStr;
    }
}
