package com.example.demo.user.service;

import com.example.demo.user.dto.request.UpdateProfileRequest;
import com.example.demo.user.dto.response.ProfileResponse;

public interface UserService {

    /**
     * Lấy thông tin profile của người dùng đang đăng nhập.
     * @param userEmail Email của người dùng hiện tại.
     * @return DTO chứa thông tin profile.
     */
    ProfileResponse getUserProfile(String userEmail);

    /**
     * Cập nhật thông tin profile của người dùng đang đăng nhập.
     * @param userEmail Email của người dùng hiện tại.
     * @param request DTO chứa thông tin cập nhật.
     * @return DTO chứa thông tin profile sau khi cập nhật.
     */
    ProfileResponse updateUserProfile(String userEmail, UpdateProfileRequest request);
}