package com.example.demo.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping; // Thêm import này
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam; // Thêm import này
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.shared.exception.BadRequestException;
import com.example.demo.auth.dto.request.ChangePasswordRequest;
import com.example.demo.auth.dto.request.ForgotPasswordRequest;
import com.example.demo.auth.dto.request.GoogleLoginRequest;
import com.example.demo.auth.dto.request.LoginRequest;
import com.example.demo.auth.dto.request.LogoutRequest;
import com.example.demo.auth.dto.request.RegisterRequest;
import com.example.demo.auth.dto.request.ResetPasswordRequest;
import com.example.demo.auth.dto.response.JwtResponse;
import com.example.demo.user.dto.response.UserDetailsResponse;
import com.example.demo.auth.service.AuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<?> registerCustomer(@Valid @RequestBody RegisterRequest request) {
        authService.registerCustomer(request);
        // Có thể thay đổi thông báo để hướng dẫn người dùng kiểm tra email
        return ResponseEntity.ok("Đăng ký thành công! Vui lòng kiểm tra email để kích hoạt tài khoản.");
    }

    // --- PHƯƠ-NG THỨC MỚI ĐƯỢC THÊM VÀO ---
    @GetMapping("/activate")
    public ResponseEntity<String> activateAccount(@RequestParam("token") String token) {
        authService.activateUserAccount(token);
        return ResponseEntity.ok("Tài khoản của bạn đã được kích hoạt thành công! Bây giờ bạn có thể đăng nhập.");
    }

    @PostMapping("/login")
    public ResponseEntity<JwtResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) { // <-- THÊM THAM SỐ
        JwtResponse jwtResponse = authService.login(request, servletRequest); // <-- TRUYỀN VÀO SERVICE
        return ResponseEntity.ok(jwtResponse);
    }
    
    @PostMapping("/refresh-token")
    public ResponseEntity<JwtResponse> refreshToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new BadRequestException("Refresh token không được cung cấp.");
        }
        String refreshToken = authHeader.substring(7);
        JwtResponse jwtResponse = authService.refreshToken(refreshToken);
        return ResponseEntity.ok(jwtResponse);
    }
    
    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok("Nếu email của bạn tồn tại trong hệ thống, bạn sẽ nhận được một liên kết để đặt lại mật khẩu.");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok("Mật khẩu của bạn đã được đặt lại thành công.");
    }
    
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()") // Yêu cầu phải đăng nhập
    public ResponseEntity<UserDetailsResponse> getCurrentUser(Authentication authentication) {
        String userEmail = authentication.getName();
        UserDetailsResponse userDetails = authService.getCurrentUserDetails(userEmail);
        return ResponseEntity.ok(userDetails);
    }
    
    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()") // Chỉ người dùng đã đăng nhập mới có thể gọi
    public ResponseEntity<String> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication) {
        
        // Lấy email của người dùng đang đăng nhập từ đối tượng Authentication
        String userEmail = authentication.getName();
        
        authService.changePassword(request, userEmail);
        return ResponseEntity.ok("Đổi mật khẩu thành công.");
    }
    
 // --- PHƯƠNG THỨC MỚI ĐƯỢC THÊM VÀO ---
    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()") // Yêu cầu người dùng phải được xác thực để đăng xuất
    public ResponseEntity<String> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.ok("Đăng xuất thành công.");
    }
    
    @PostMapping("/google")
    public ResponseEntity<JwtResponse> loginWithGoogle(
            @Valid @RequestBody GoogleLoginRequest request,
            HttpServletRequest servletRequest) { // <-- THÊM THAM SỐ
        JwtResponse jwtResponse = authService.loginWithGoogle(request.getIdToken(), servletRequest); // <-- TRUYỀN VÀO SERVICE
        return ResponseEntity.ok(jwtResponse);
    }
}