// src/test/java/com/example/demo/auth/controller/AuthControllerTest.java
package com.example.demo.auth.controller;

import com.example.demo.auth.dto.request.LoginRequest;
import com.example.demo.auth.dto.request.RegisterRequest;
import com.example.demo.auth.dto.response.JwtResponse;
import com.example.demo.auth.service.AuthService;
import com.example.demo.config.SecurityConfig; // Import SecurityConfig to disable CSRF etc.
import com.example.demo.config.security.JwtAuthenticationFilter; // Needed by SecurityConfig
import com.example.demo.shared.util.JwtUtil; // Needed by Filter
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser; // For testing authenticated endpoints
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*; // For csrf
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
// Import SecurityConfig to apply security filters (like CSRF disabling) correctly during testing
// Also mock beans required by SecurityConfig/Filters if they aren't auto-mocked by @WebMvcTest
//@Import(SecurityConfig.class) // Import your actual security config
@AutoConfigureMockMvc(addFilters = false) // 🔥 Tắt Spring Security filters
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper; // For converting objects to JSON

    @MockBean
    private AuthService authService;

    // Mock beans required by SecurityConfig and JwtAuthenticationFilter
    @MockBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean private JwtUtil jwtUtil; // Mock JwtUtil used by the filter
    @MockBean private org.springframework.security.core.userdetails.UserDetailsService userDetailsService; // Mock UserDetailsService

    @Test
    void registerCustomer_ValidRequest_ReturnsOk() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setPassword("password123");
        request.setFullname("Test User");
        request.setPhoneNumber("123456789");

        // No return value needed, just verify the service method is called
        doNothing().when(authService).registerCustomer(any(RegisterRequest.class));

        mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf()) // Add CSRF token if enabled (though usually disabled for APIs)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("Đăng ký thành công! Vui lòng kiểm tra email để kích hoạt tài khoản."));

        verify(authService).registerCustomer(any(RegisterRequest.class));
    }

     @Test
    void registerCustomer_InvalidRequest_ReturnsBadRequest() throws Exception {
        RegisterRequest request = new RegisterRequest(); // Missing required fields
        request.setEmail("invalid-email"); // Invalid format

        mockMvc.perform(post("/api/v1/auth/register")
                         .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest()); // Expect validation to fail

        verify(authService, never()).registerCustomer(any());
    }

    @Test
    void activateAccount_ValidToken_ReturnsOk() throws Exception {
        String token = "validActivationToken";
        doNothing().when(authService).activateUserAccount(token);

        mockMvc.perform(get("/api/v1/auth/activate")
                        .param("token", token))
                .andExpect(status().isOk())
                .andExpect(content().string("Tài khoản của bạn đã được kích hoạt thành công! Bây giờ bạn có thể đăng nhập."));

        verify(authService).activateUserAccount(token);
    }

    // Add similar test for activateAccount with invalid/expired token if service throws exception mapped to HTTP status

    @Test
    void login_ValidCredentials_ReturnsJwtResponse() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("password");

        JwtResponse jwtResponse = JwtResponse.builder().accessToken("access").refreshToken("refresh").build();

        when(authService.login(any(LoginRequest.class), any())).thenReturn(jwtResponse);

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access"))
                .andExpect(jsonPath("$.refreshToken").value("refresh"));

        verify(authService).login(any(LoginRequest.class), any());
    }

    // Add test for login with invalid credentials (service should throw exception handled by Spring Security/ControllerAdvice)

    // Example for an authenticated endpoint test
    @Test
    @WithMockUser(username = "test@example.com") // Simulate an authenticated user
    void getCurrentUser_Authenticated_ReturnsUserDetails() throws Exception {
        // Mock the service response
        com.example.demo.user.dto.response.UserDetailsResponse userDetails =
            com.example.demo.user.dto.response.UserDetailsResponse.builder()
                .id(1)
                .email("test@example.com")
                .fullname("Test User")
                .userType("CUSTOMER")
                .build();
        when(authService.getCurrentUserDetails("test@example.com")).thenReturn(userDetails);

        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.fullname").value("Test User"));

        verify(authService).getCurrentUserDetails("test@example.com");
    }

     @Test
    void getCurrentUser_Unauthenticated_ReturnsUnauthorized() throws Exception {
         // No @WithMockUser

        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized()); // Expect 401 due to SecurityConfig

        verify(authService, never()).getCurrentUserDetails(anyString());
    }


    // Add more tests for: refreshToken, forgotPassword, resetPassword, changePassword, logout, google login endpoints
}