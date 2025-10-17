package com.example.demo.auth.service;


import com.example.demo.auth.dto.request.ChangePasswordRequest;
import com.example.demo.auth.dto.request.ForgotPasswordRequest;
import com.example.demo.auth.dto.request.LoginRequest;
import com.example.demo.auth.dto.request.RegisterRequest;
import com.example.demo.auth.dto.request.ResetPasswordRequest;
import com.example.demo.auth.dto.response.JwtResponse;
import com.example.demo.user.dto.response.UserDetailsResponse;

import jakarta.servlet.http.HttpServletRequest;

public interface AuthService {

    void registerCustomer(RegisterRequest request);

    void activateUserAccount(String token);

    JwtResponse login(LoginRequest request, HttpServletRequest servletRequest);

    JwtResponse refreshToken(String refreshToken);

    void forgotPassword(ForgotPasswordRequest request);

    void resetPassword(ResetPasswordRequest request);

    void changePassword(ChangePasswordRequest request, String userEmail);

    UserDetailsResponse getCurrentUserDetails(String userEmail);

    void logout(String refreshToken);

    JwtResponse loginWithGoogle(String idTokenString, HttpServletRequest servletRequest);
}
