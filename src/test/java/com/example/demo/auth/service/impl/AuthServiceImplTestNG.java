// src/test/java/com/example/demo/auth/service/impl/AuthServiceImplTestNG.java
package com.example.demo.auth.service.impl;

// Import TestNG annotations
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;
// Import Mockito TestNG listener
import org.mockito.testng.MockitoTestNGListener;

// Các import khác giữ nguyên từ phiên bản JUnit
import com.example.demo.auth.dto.request.LoginRequest;
import com.example.demo.auth.dto.request.RegisterRequest;
import com.example.demo.auth.dto.response.JwtResponse;
import com.example.demo.auth.entity.UserActivationToken;
import com.example.demo.auth.entity.UserSession;
import com.example.demo.auth.repository.UserActivationTokenRepository;
import com.example.demo.auth.repository.UserSessionRepository;
import com.example.demo.shared.exception.BadRequestException;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.example.demo.shared.exception.ResourceNotFoundException;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Listeners(MockitoTestNGListener.class) // Sử dụng listener của Mockito cho TestNG
public class AuthServiceImplTestNG {

    @Mock private UserRepository userRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private CustomerMapper customerMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtUtil jwtUtil;
    @Mock private UserSessionRepository userSessionRepository;
    @Mock private UserActivityLogService userActivityLogService;
    @Mock private UserActivationTokenRepository activationTokenRepository;
    @Mock private EmailService emailService;
    @Mock private UserMapper userMapper;
    @Mock private HttpServletRequest httpServletRequest;
    @Mock private Authentication authentication;


    @InjectMocks
    private AuthServiceImpl authService;

    private User user;
    private Customer customer;
    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;

    @BeforeMethod // Thay @BeforeEach bằng @BeforeMethod
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
        user.setCustomer(customer);

        registerRequest = new RegisterRequest();
        registerRequest.setEmail("new@example.com");
        registerRequest.setPassword("password123");
        registerRequest.setFullname("New User");
        registerRequest.setPhoneNumber("987654321");

        loginRequest = new LoginRequest();
        loginRequest.setEmail("test@example.com");
        loginRequest.setPassword("password123");
    }

    @Test // Giữ nguyên @Test
    void registerCustomer_Success() {
        when(userRepository.existsByEmail(registerRequest.getEmail())).thenReturn(false);
        when(customerRepository.existsByPhoneNumber(registerRequest.getPhoneNumber())).thenReturn(false);
        when(passwordEncoder.encode(registerRequest.getPassword())).thenReturn("encodedPassword");
        when(customerMapper.toCustomerEntity(registerRequest)).thenReturn(Customer.builder().fullname("New User").phoneNumber("987654321").build());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User savedUser = invocation.getArgument(0);
            savedUser.setId(2);
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

    @Test(expectedExceptions = BadRequestException.class) // TestNG dùng expectedExceptions
    void registerCustomer_EmailExists() {
        when(userRepository.existsByEmail(registerRequest.getEmail())).thenReturn(true);
        try {
            authService.registerCustomer(registerRequest);
        } catch (BadRequestException e) {
            Assert.assertEquals(e.getMessage(), "Email đã được sử dụng."); // Kiểm tra message
            verify(userRepository, never()).save(any(User.class));
            throw e; // Ném lại exception để TestNG bắt
        }
    }

    @Test(expectedExceptions = BadRequestException.class)
    void registerCustomer_PhoneExists() {
        when(userRepository.existsByEmail(registerRequest.getEmail())).thenReturn(false);
        when(customerRepository.existsByPhoneNumber(registerRequest.getPhoneNumber())).thenReturn(true);
         try {
            authService.registerCustomer(registerRequest);
         } catch (BadRequestException e) {
             Assert.assertEquals(e.getMessage(), "Số điện thoại đã được sử dụng.");
             verify(userRepository, never()).save(any(User.class));
            throw e;
        }
    }

    @Test
    void activateUserAccount_Success() {
        User pendingUser = User.builder().id(1).email("pending@example.com").status(UserStatus.PENDING_ACTIVATION).build();
        UserActivationToken token = new UserActivationToken(pendingUser);
        token.setExpiresAt(OffsetDateTime.now().plusHours(1));

        when(activationTokenRepository.findByToken(token.getToken())).thenReturn(Optional.of(token));
        when(userRepository.save(any(User.class))).thenReturn(pendingUser);

        authService.activateUserAccount(token.getToken());

        Assert.assertEquals(pendingUser.getStatus(), UserStatus.ACTIVE); // Thay assertEquals
        verify(userRepository).save(pendingUser);
        verify(activationTokenRepository).delete(token);
    }

    @Test(expectedExceptions = BadRequestException.class)
    void activateUserAccount_InvalidToken() {
        when(activationTokenRepository.findByToken("invalidToken")).thenReturn(Optional.empty());
         try {
            authService.activateUserAccount("invalidToken");
         } catch (BadRequestException e) {
             Assert.assertEquals(e.getMessage(), "Token kích hoạt không hợp lệ.");
             verify(userRepository, never()).save(any(User.class));
             verify(activationTokenRepository, never()).delete(any());
            throw e;
        }
    }

     @Test(expectedExceptions = BadRequestException.class)
    void activateUserAccount_ExpiredToken() {
        User pendingUser = User.builder().id(1).email("pending@example.com").status(UserStatus.PENDING_ACTIVATION).build();
        UserActivationToken token = new UserActivationToken(pendingUser);
        token.setExpiresAt(OffsetDateTime.now().minusHours(1));

        when(activationTokenRepository.findByToken(token.getToken())).thenReturn(Optional.of(token));

        try {
            authService.activateUserAccount(token.getToken());
        } catch (BadRequestException e) {
            Assert.assertEquals(e.getMessage(), "Token kích hoạt đã hết hạn.");
            verify(activationTokenRepository).delete(token);
            verify(userRepository, never()).save(any(User.class));
            throw e;
        }
    }

     @Test(expectedExceptions = BadRequestException.class)
    void activateUserAccount_AlreadyActive() {
        User activeUser = User.builder().id(1).email("active@example.com").status(UserStatus.ACTIVE).build();
        UserActivationToken token = new UserActivationToken(activeUser);
        token.setExpiresAt(OffsetDateTime.now().plusHours(1));

        when(activationTokenRepository.findByToken(token.getToken())).thenReturn(Optional.of(token));

         try {
            authService.activateUserAccount(token.getToken());
         } catch (BadRequestException e) {
             Assert.assertEquals(e.getMessage(), "Tài khoản này đã được kích hoạt trước đó.");
             verify(userRepository, never()).save(any(User.class));
             verify(activationTokenRepository, never()).delete(any());
            throw e;
        }
    }

    @Test
    void login_Success() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(userRepository.findByEmail(loginRequest.getEmail())).thenReturn(Optional.of(user));
        when(userSessionRepository.countByUserId(user.getId())).thenReturn(0L);
        when(jwtUtil.generateAccessToken(user)).thenReturn("accessToken");
        when(jwtUtil.generateRefreshToken(user)).thenReturn("refreshToken");
        when(httpServletRequest.getHeader("User-Agent")).thenReturn("TestAgent");
        when(userSessionRepository.save(any(UserSession.class))).thenReturn(new UserSession());

        JwtResponse response = authService.login(loginRequest, httpServletRequest);

        Assert.assertNotNull(response); // Thay assertNotNull
        Assert.assertEquals(response.getAccessToken(), "accessToken");
        Assert.assertEquals(response.getRefreshToken(), "refreshToken");
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(userRepository).findByEmail("test@example.com");
        verify(userSessionRepository).save(any(UserSession.class));
    }

    @Test(expectedExceptions = ResourceNotFoundException.class)
    void login_UserNotFound() {
         when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(userRepository.findByEmail(loginRequest.getEmail())).thenReturn(Optional.empty());

        try {
             authService.login(loginRequest, httpServletRequest);
        } finally {
            // Verify trong finally để đảm bảo nó được chạy ngay cả khi có exception
             verify(userSessionRepository, never()).save(any(UserSession.class));
        }
    }

    @Test(expectedExceptions = DisabledException.class)
    void login_UserNotActive_PendingActivation() {
        user.setStatus(UserStatus.PENDING_ACTIVATION);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(userRepository.findByEmail(loginRequest.getEmail())).thenReturn(Optional.of(user));

        try {
            authService.login(loginRequest, httpServletRequest);
        } catch (DisabledException e) {
            Assert.assertEquals(e.getMessage(), "Tài khoản của bạn chưa được kích hoạt. Vui lòng kiểm tra email.");
             verify(userSessionRepository, never()).save(any(UserSession.class));
            throw e;
        }
    }

    @Test(expectedExceptions = DisabledException.class)
    void login_UserNotActive_Suspended() {
        user.setStatus(UserStatus.SUSPENDED);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(userRepository.findByEmail(loginRequest.getEmail())).thenReturn(Optional.of(user));

        try {
            authService.login(loginRequest, httpServletRequest);
        } catch (DisabledException e) {
            Assert.assertEquals(e.getMessage(), "Tài khoản của bạn đã bị khóa. Vui lòng liên hệ quản trị viên.");
            verify(userSessionRepository, never()).save(any(UserSession.class));
            throw e;
        }
    }
}