package com.vaultbank.service.impl;

import com.vaultbank.dto.request.LoginRequest;
import com.vaultbank.dto.request.RegisterRequest;
import com.vaultbank.dto.request.TokenRefreshRequest;
import com.vaultbank.dto.response.AuthResponse;
import com.vaultbank.dto.response.TokenRefreshResponse;
import com.vaultbank.entity.RefreshToken;
import com.vaultbank.entity.Role;
import com.vaultbank.entity.User;
import com.vaultbank.exception.DuplicateEmailException;
import com.vaultbank.exception.TokenRefreshException;
import com.vaultbank.repository.RefreshTokenRepository;
import com.vaultbank.repository.UserRepository;
import com.vaultbank.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider tokenProvider;
    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "refreshTokenExpirationMs", 604800000L);
    }

    @Test
    void register_Success() {
        RegisterRequest request = RegisterRequest.builder()
                .email("test@vaultbank.com")
                .password("password")
                .firstName("John")
                .lastName("Doe")
                .role(Role.ROLE_USER)
                .build();

        User user = User.builder()
                .email(request.getEmail())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .role(request.getRole())
                .build();

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(tokenProvider.generateAccessTokenFromEmailAndRole(anyString(), anyString())).thenReturn("accessToken");
        when(tokenProvider.generateRefreshToken(anyString())).thenReturn("refreshToken");

        AuthResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("test@vaultbank.com", response.getEmail());
        assertEquals("accessToken", response.getAccessToken());
        assertEquals("refreshToken", response.getRefreshToken());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void register_DuplicateEmail_ThrowsException() {
        RegisterRequest request = RegisterRequest.builder()
                .email("test@vaultbank.com")
                .password("password")
                .build();

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);

        assertThrows(DuplicateEmailException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void login_Success() {
        LoginRequest request = LoginRequest.builder()
                .email("test@vaultbank.com")
                .password("password")
                .build();

        User user = User.builder()
                .email("test@vaultbank.com")
                .role(Role.ROLE_USER)
                .build();

        Authentication authentication = mock(Authentication.class);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(tokenProvider.generateAccessToken(authentication)).thenReturn("accessToken");
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
        when(tokenProvider.generateRefreshToken(anyString())).thenReturn("refreshToken");

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("accessToken", response.getAccessToken());
        assertEquals("refreshToken", response.getRefreshToken());
    }

    @Test
    void refreshToken_Success() {
        TokenRefreshRequest request = TokenRefreshRequest.builder().refreshToken("validRefreshToken").build();
        User user = User.builder().email("test@vaultbank.com").role(Role.ROLE_USER).build();
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token("validRefreshToken")
                .expiryDate(Instant.now().plusSeconds(3600))
                .build();

        when(refreshTokenRepository.findByToken("validRefreshToken")).thenReturn(Optional.of(refreshToken));
        when(tokenProvider.validateToken("validRefreshToken")).thenReturn(true);
        when(tokenProvider.generateAccessTokenFromEmailAndRole(anyString(), anyString())).thenReturn("newAccessToken");
        when(tokenProvider.generateRefreshToken(anyString())).thenReturn("newRefreshToken");

        TokenRefreshResponse response = authService.refreshToken(request);

        assertNotNull(response);
        assertEquals("newAccessToken", response.getAccessToken());
        assertEquals("newRefreshToken", response.getRefreshToken());
    }

    @Test
    void refreshToken_ExpiredToken_ThrowsException() {
        TokenRefreshRequest request = TokenRefreshRequest.builder().refreshToken("expiredRefreshToken").build();
        User user = User.builder().email("test@vaultbank.com").role(Role.ROLE_USER).build();
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token("expiredRefreshToken")
                .expiryDate(Instant.now().minusSeconds(3600))
                .build();

        when(refreshTokenRepository.findByToken("expiredRefreshToken")).thenReturn(Optional.of(refreshToken));

        assertThrows(TokenRefreshException.class, () -> authService.refreshToken(request));
        verify(refreshTokenRepository, times(1)).delete(refreshToken);
    }
}
