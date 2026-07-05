package com.vaultbank.service;

import com.vaultbank.dto.request.UpdateProfileRequest;
import com.vaultbank.dto.response.UserResponse;

import java.util.List;

public interface UserService {
    UserResponse getProfile(String email);
    UserResponse updateProfile(String email, UpdateProfileRequest request);
    List<UserResponse> getAllUsers(int page, int size);
}
