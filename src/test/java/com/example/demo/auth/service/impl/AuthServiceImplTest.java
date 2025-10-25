// src/test/java/com/example/demo/auth/service/impl/AuthServiceImplTest.java
package com.example.demo.auth.service.impl;

import com.example.demo.auth.dto.request.LoginRequest;
import com.example.demo.auth.dto.request.RegisterRequest;
import com.example.demo.auth.dto.response.JwtResponse;
import com.example.demo.auth.entity.UserActivationToken;
import com.example.demo.auth.entity.UserSession;
import com.example.demo.auth.repository.UserActivationTokenRepository;
import com.example.demo.auth.repository.UserSessionRepository;
import com.example.demo.shared.exception.BadRequestException;
import com.example.demo.shared.exception.ResourceNotFoundException;
import com.example.demo.shared.service.EmailService;
import com.example.demo.shared.util.JwtUtil;
import com.example.demo.user.entity.Customer;
import com.example.demo.user.entity.User;
import com.example.demo.user.entity.UserStatus;
import com.example.demo.user.mapper.CustomerMapper;
import com.example.demo.user.mapper.UserMapper;
import com.example.demo.user.repository.CustomerRepository;
import com.example.demo.user.repository.UserRepository;
import com.example.demo.user.service.UserActivityLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private CustomerMapper customerMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtUtil jwtUtil;
    @Mock private UserSessionRepository userSessionRepository;
    @Mock private UserActivityLogService userActivityLogService; // Mocked, but not used in these basic tests
    @Mock private UserActivationTokenRepository activationTokenRepository;
    @Mock private EmailService emailService;
    @Mock private UserMapper userMapper; // Mocked, assuming it's used elsewhere
    @Mock private HttpServletRequest httpServletRequest;
    @Mock private Authentication authentication;


    @InjectMocks
    private AuthServiceImpl authService;

    private User user;
    private Customer customer;
    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1)
                .email("test@example.com")
                .password("encodedPassword")
                .status(UserStatus.ACTIVE)
                .build();

        customer = Customer.builder()
                .id(1)
                .fullname("Test User")
                .phoneNumber("123456789")
                .user(user)
                .build();
        user.setCustomer(customer); // Link back

        registerRequest = new RegisterRequest();
        registerRequest.setEmail("new@example.com");
        registerRequest.setPassword("password123");
        registerRequest.setFullname("New User");
        registerRequest.setPhoneNumber("987654321");

        loginRequest = new LoginRequest();
        loginRequest.setEmail("test@example.com");
        loginRequest.setPassword("password123");
    }

    @Test
    void registerCustomer_Success() {
        when(userRepository.existsByEmail(registerRequest.getEmail())).thenReturn(false);
        when(customerRepository.existsByPhoneNumber(registerRequest.getPhoneNumber())).thenReturn(false);
        when(passwordEncoder.encode(registerRequest.getPassword())).thenReturn("encodedPassword");
        // Assume customerMapper maps correctly
        when(customerMapper.toCustomerEntity(registerRequest)).thenReturn(Customer.builder().fullname("New User").phoneNumber("987654321").build());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User savedUser = invocation.getArgument(0);
            savedUser.setId(2); // Simulate saving and getting an ID
            // Simulate cascade save for customer
            if (savedUser.getCustomer() != null) {
                savedUser.getCustomer().setId(2);
                savedUser.getCustomer().setUser(savedUser);
            }
            return savedUser;
        });
        when(activationTokenRepository.save(any(UserActivationToken.class))).thenReturn(new UserActivationToken());

        authService.registerCustomer(registerRequest);

        verify(userRepository).existsByEmail("new@example.com");
        verify(customerRepository).existsByPhoneNumber("987654321");
        verify(passwordEncoder).encode("password123");
        verify(userRepository).save(any(User.class));
        verify(activationTokenRepository).save(any(UserActivationToken.class));
        verify(emailService).sendEmail(eq("new@example.com"), anyString(), anyString());
    }

    @Test
    void registerCustomer_EmailExists() {
        when(userRepository.existsByEmail(registerRequest.getEmail())).thenReturn(true);

        BadRequestException exception = assertThrows(BadRequestException.class, () -> authService.registerCustomer(registerRequest));
        assertEquals("Email đã được sử dụng.", exception.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

     @Test
    void registerCustomer_PhoneExists() {
        when(userRepository.existsByEmail(registerRequest.getEmail())).thenReturn(false);
        when(customerRepository.existsByPhoneNumber(registerRequest.getPhoneNumber())).thenReturn(true);

        BadRequestException exception = assertThrows(BadRequestException.class, () -> authService.registerCustomer(registerRequest));
        assertEquals("Số điện thoại đã được sử dụng.", exception.getMessage());
         verify(userRepository, never()).save(any(User.class));
    }


    @Test
    void activateUserAccount_Success() {
        User pendingUser = User.builder().id(1).email("pending@example.com").status(UserStatus.PENDING_ACTIVATION).build();
        UserActivationToken token = new UserActivationToken(pendingUser);
        token.setExpiresAt(OffsetDateTime.now().plusHours(1)); // Not expired

        when(activationTokenRepository.findByToken(token.getToken())).thenReturn(Optional.of(token));
        when(userRepository.save(any(User.class))).thenReturn(pendingUser); // Assume save works

        authService.activateUserAccount(token.getToken());

        assertEquals(UserStatus.ACTIVE, pendingUser.getStatus());
        verify(userRepository).save(pendingUser);
        verify(activationTokenRepository).delete(token);
    }

    @Test
    void activateUserAccount_InvalidToken() {
        when(activationTokenRepository.findByToken("invalidToken")).thenReturn(Optional.empty());

        BadRequestException exception = assertThrows(BadRequestException.class, () -> authService.activateUserAccount("invalidToken"));
        assertEquals("Token kích hoạt không hợp lệ.", exception.getMessage());
         verify(userRepository, never()).save(any(User.class));
         verify(activationTokenRepository, never()).delete(any());
    }

     @Test
    void activateUserAccount_ExpiredToken() {
        User pendingUser = User.builder().id(1).email("pending@example.com").status(UserStatus.PENDING_ACTIVATION).build();
        UserActivationToken token = new UserActivationToken(pendingUser);
        token.setExpiresAt(OffsetDateTime.now().minusHours(1)); // Expired

        when(activationTokenRepository.findByToken(token.getToken())).thenReturn(Optional.of(token));

        BadRequestException exception = assertThrows(BadRequestException.class, () -> authService.activateUserAccount(token.getToken()));
        assertEquals("Token kích hoạt đã hết hạn.", exception.getMessage());
        verify(activationTokenRepository).delete(token); // Should delete expired token
         verify(userRepository, never()).save(any(User.class));
    }

     @Test
    void activateUserAccount_AlreadyActive() {
        User activeUser = User.builder().id(1).email("active@example.com").status(UserStatus.ACTIVE).build();
        UserActivationToken token = new UserActivationToken(activeUser);
        token.setExpiresAt(OffsetDateTime.now().plusHours(1));

        when(activationTokenRepository.findByToken(token.getToken())).thenReturn(Optional.of(token));

        BadRequestException exception = assertThrows(BadRequestException.class, () -> authService.activateUserAccount(token.getToken()));
        assertEquals("Tài khoản này đã được kích hoạt trước đó.", exception.getMessage());
         verify(userRepository, never()).save(any(User.class));
         verify(activationTokenRepository, never()).delete(any()); // Don't delete if activation failed due to status
    }

    @Test
    void login_Success() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(userRepository.findByEmail(loginRequest.getEmail())).thenReturn(Optional.of(user)); // User is ACTIVE
        when(userSessionRepository.countByUserId(user.getId())).thenReturn(0L); // No existing sessions
        when(jwtUtil.generateAccessToken(user)).thenReturn("accessToken");
        when(jwtUtil.generateRefreshToken(user)).thenReturn("refreshToken");
        when(httpServletRequest.getHeader("User-Agent")).thenReturn("TestAgent");
        // Simulate getClientIp behaviour or mock it if complex
        // when(authService.getClientIp(httpServletRequest)).thenReturn("127.0.0.1"); // Can't mock private method directly easily
        when(userSessionRepository.save(any(UserSession.class))).thenReturn(new UserSession());

        JwtResponse response = authService.login(loginRequest, httpServletRequest);

        assertNotNull(response);
        assertEquals("accessToken", response.getAccessToken());
        assertEquals("refreshToken", response.getRefreshToken());
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(userRepository).findByEmail("test@example.com");
        verify(userSessionRepository).save(any(UserSession.class));
    }

    @Test
    void login_UserNotFound() {
         when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        // Simulate user not found AFTER successful authentication (unlikely but possible if deleted between steps)
        when(userRepository.findByEmail(loginRequest.getEmail())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> authService.login(loginRequest, httpServletRequest));
        verify(userSessionRepository, never()).save(any(UserSession.class));
    }

     @Test
    void login_UserNotActive_PendingActivation() {
        user.setStatus(UserStatus.PENDING_ACTIVATION);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(userRepository.findByEmail(loginRequest.getEmail())).thenReturn(Optional.of(user));

        DisabledException exception = assertThrows(DisabledException.class, () -> authService.login(loginRequest, httpServletRequest));
        assertEquals("Tài khoản của bạn chưa được kích hoạt. Vui lòng kiểm tra email.", exception.getMessage());
        verify(userSessionRepository, never()).save(any(UserSession.class));
    }

      @Test
    void login_UserNotActive_Suspended() {
        user.setStatus(UserStatus.SUSPENDED);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(userRepository.findByEmail(loginRequest.getEmail())).thenReturn(Optional.of(user));

        DisabledException exception = assertThrows(DisabledException.class, () -> authService.login(loginRequest, httpServletRequest));
        assertEquals("Tài khoản của bạn đã bị khóa. Vui lòng liên hệ quản trị viên.", exception.getMessage());
        verify(userSessionRepository, never()).save(any(UserSession.class));
    }

    // Add more tests for refreshToken, loginWithGoogle, forgotPassword, resetPassword, changePassword, getCurrentUserDetails, logout
    // Testing Google login requires mocking GoogleIdTokenVerifier and related classes.
}