package com.example.demo.user.controller;

import com.example.demo.progress.dto.response.ProgressChartDataResponse; // *** THÊM IMPORT ***
import com.example.demo.progress.service.ProgressService;
import com.example.demo.user.dto.request.UpdateProfileRequest;
import com.example.demo.user.dto.response.ProfileResponse;
import com.example.demo.user.dto.response.UserDetailsResponse;
import com.example.demo.user.dto.response.UserStatsResponse;
import com.example.demo.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List; // *** THÊM IMPORT ***

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final ProgressService progressService;

    @GetMapping("/profile")
    @PreAuthorize("isAuthenticated()") // Yêu cầu đăng nhập
    public ResponseEntity<ProfileResponse> getMyProfile(Authentication authentication) {
        String userEmail = authentication.getName();
        ProfileResponse profile = userService.getUserProfile(userEmail);
        return ResponseEntity.ok(profile);
    }

    @PutMapping("/profile")
    @PreAuthorize("isAuthenticated()") // Yêu cầu đăng nhập
    public ResponseEntity<ProfileResponse> updateMyProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateProfileRequest request) {
        String userEmail = authentication.getName();
        ProfileResponse updatedProfile = userService.updateUserProfile(userEmail, request);
        return ResponseEntity.ok(updatedProfile);
    }
    
    @GetMapping("/me/stats")
    @PreAuthorize("isAuthenticated()") // Yêu cầu đăng nhập
    public ResponseEntity<UserStatsResponse> getMyStats(Authentication authentication) {
        String userEmail = authentication.getName();
        UserStatsResponse stats = progressService.getUserStats(userEmail); // Gọi service mới
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/me/progress-chart")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ProgressChartDataResponse>> getMyProgressChartData(Authentication authentication) {
        String userEmail = authentication.getName();
        List<ProgressChartDataResponse> chartData = progressService.getProgressChartData(userEmail);
        return ResponseEntity.ok(chartData);
    }
}