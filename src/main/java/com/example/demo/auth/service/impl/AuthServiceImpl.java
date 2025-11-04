package com.example.demo.auth.service.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.shared.exception.BadRequestException;
import com.example.demo.shared.exception.ResourceNotFoundException;
import com.example.demo.shared.service.EmailService;
import com.example.demo.shared.util.JwtUtil;
import com.example.demo.auth.dto.request.ChangePasswordRequest;
import com.example.demo.auth.dto.request.ForgotPasswordRequest;
import com.example.demo.auth.dto.request.LoginRequest;
import com.example.demo.auth.dto.request.RegisterRequest;
import com.example.demo.auth.dto.request.ResetPasswordRequest;
import com.example.demo.auth.dto.response.JwtResponse;
import com.example.demo.auth.entity.PasswordResetToken;
import com.example.demo.auth.entity.UserActivationToken;
import com.example.demo.auth.entity.UserSession;
import com.example.demo.user.dto.response.UserDetailsResponse;
import com.example.demo.user.entity.Customer;
import com.example.demo.user.entity.Role;
import com.example.demo.user.entity.User;
import com.example.demo.user.entity.UserStatus;
import com.example.demo.user.mapper.CustomerMapper;
import com.example.demo.user.mapper.UserMapper;
import com.example.demo.user.repository.CustomerRepository;
import com.example.demo.auth.repository.PasswordResetTokenRepository;
import com.example.demo.user.repository.RoleRepository;
import com.example.demo.auth.repository.UserActivationTokenRepository;
import com.example.demo.user.repository.UserRepository;
import com.example.demo.auth.service.AuthService;
import com.example.demo.user.service.UserActivityLogService;
import com.example.demo.auth.repository.UserSessionRepository;
import java.time.OffsetDateTime;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor // Tự động inject các dependency final
@Transactional
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final RoleRepository roleRepository;
    private final CustomerMapper customerMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserSessionRepository userSessionRepository;
    private final UserActivityLogService userActivityLogService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final UserMapper userMapper; // <-- Dependency này giờ rất quan trọng
    
    private final UserActivationTokenRepository activationTokenRepository;
    private final EmailService emailService;
    
    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String googleClientId;
    
    @Value("${app.jwt.refresh-token-expiration-ms}") 
    private long refreshTokenExpirationMs;
    
    @Value("${app.security.max-concurrent-sessions}")
    private int maxConcurrentSessions; // <-- BIẾN MỚI
    
    // (Thêm biến môi trường cho frontend URL, nếu không có thì hardcode)
    // @Value("${app.frontend.base-url}")
    // private String frontendBaseUrl; // Ví dụ: http://localhost:5173


    @Override
    public void registerCustomer(RegisterRequest request) {
        // 1. Validate dữ liệu
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email đã được sử dụng.");
        }
        if (customerRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new BadRequestException("Số điện thoại đã được sử dụng.");
        }

        // 2. Tạo User
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setStatus(UserStatus.PENDING_ACTIVATION);
        // user.setAuthProvider("LOCAL"); // Không cần, vì "LOCAL" là default trong Entity
        
        // 3. Tạo Customer
        Customer customer = customerMapper.toCustomerEntity(request);

        // 4. Thiết lập mối quan hệ hai chiều
        customer.setUser(user);
        user.setCustomer(customer);

        // 5. Lưu User (Cascade từ User sẽ tự động lưu Customer)
        userRepository.save(user);

        // 6. Tạo token kích hoạt và gửi email
        UserActivationToken activationToken = new UserActivationToken(user);
        activationTokenRepository.save(activationToken);

        // (Từ P1) Sửa link trỏ về Frontend
        String frontendUrl = "http://localhost:5173"; 
        String activationLink = frontendUrl + "/activate?token=" + activationToken.getToken();

        String emailBody = "<h1>Chào mừng bạn đến với MindRevol!</h1>" +
                           "<p>Vui lòng nhấp vào liên kết dưới đây để kích hoạt tài khoản của bạn:</p>" +
                           "<a href=\"" + activationLink + "\">Kích hoạt ngay</a>" +
                           "<p>Liên kết này sẽ hết hạn trong 24 giờ.</p>";
        emailService.sendEmail(user.getEmail(), "Kích hoạt tài khoản MindRevol", emailBody);
    }

    @Override
    public void activateUserAccount(String token) {
        UserActivationToken activationToken = activationTokenRepository.findByToken(token)
                .orElseThrow(() -> new BadRequestException("Token kích hoạt không hợp lệ."));

        if (activationToken.isExpired()) {
            activationTokenRepository.delete(activationToken);
            throw new BadRequestException("Token kích hoạt đã hết hạn.");
        }

        User user = activationToken.getUser();
        if (user.getStatus() != UserStatus.PENDING_ACTIVATION) {
             throw new BadRequestException("Tài khoản này đã được kích hoạt trước đó.");
        }
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        activationTokenRepository.delete(activationToken);
    }

    @Override
    public JwtResponse login(LoginRequest request, HttpServletRequest servletRequest) { 
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại."));

        if (user.getStatus() != UserStatus.ACTIVE) {
            String message;
            if (user.getStatus() == UserStatus.PENDING_ACTIVATION) {
                message = "Tài khoản của bạn chưa được kích hoạt. Vui lòng kiểm tra email.";
            } else { // UserStatus.SUSPENDED
                message = "Tài khoản của bạn đã bị khóa. Vui lòng liên hệ quản trị viên.";
            }
            throw new DisabledException(message);
        }

        long sessionCount = userSessionRepository.countByUserId(user.getId());
        if (sessionCount >= maxConcurrentSessions) {
            userSessionRepository.findFirstByUserIdOrderByCreatedAtAsc(user.getId())
                    .ifPresent(userSessionRepository::delete);
        }

        String accessToken = jwtUtil.generateAccessToken(user);
        String refreshToken = jwtUtil.generateRefreshToken(user);
        
        String userAgent = servletRequest.getHeader("User-Agent");
        String ipAddress = getClientIp(servletRequest);
        
        UserSession session = UserSession.builder()
                .user(user)
                .refreshToken(refreshToken)
                .expiresAt(OffsetDateTime.now().plusSeconds(refreshTokenExpirationMs / 1000))
                .userAgent(userAgent) 
                .ipAddress(ipAddress)   
                .build();
        userSessionRepository.save(session);

        return JwtResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    private String getClientIp(HttpServletRequest request) {
        String remoteAddr = "";
        if (request != null) {
            remoteAddr = request.getHeader("X-FORWARDED-FOR");
            if (remoteAddr == null || "".equals(remoteAddr)) {
                remoteAddr = request.getRemoteAddr();
            }
        }
        return remoteAddr;
    }
    
    // (Từ P5) Kích hoạt Refresh Token Rotation
    @Override
    public JwtResponse refreshToken(String refreshToken) {
        UserSession session = userSessionRepository.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new BadRequestException("Refresh token không hợp lệ hoặc đã bị thu hồi."));

        if (session.getExpiresAt().isBefore(OffsetDateTime.now())) {
            userSessionRepository.delete(session); 
            throw new BadRequestException("Refresh token đã hết hạn.");
        }

        User user = session.getUser();
        String newAccessToken = jwtUtil.generateAccessToken(user);
        String newRefreshToken = jwtUtil.generateRefreshToken(user); // Tạo token mới

        // Cập nhật session với token mới
        session.setRefreshToken(newRefreshToken);
        session.setExpiresAt(OffsetDateTime.now().plusSeconds(refreshTokenExpirationMs / 1000));
        userSessionRepository.save(session);

        return JwtResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken) // Trả về token mới
                .build();
    }
    
    @Override
    public JwtResponse loginWithGoogle(String idTokenString, HttpServletRequest servletRequest) { 
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                throw new BadRequestException("Token Google không hợp lệ.");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail();
            User user = userRepository.findByEmail(email)
                    .orElseGet(() -> registerNewUserFromGoogle(payload)); // Hàm này sẽ set "GOOGLE"
            
            long sessionCount = userSessionRepository.countByUserId(user.getId());
            if (sessionCount >= maxConcurrentSessions) {
                userSessionRepository.findFirstByUserIdOrderByCreatedAtAsc(user.getId())
                        .ifPresent(userSessionRepository::delete);
            }

            String accessToken = jwtUtil.generateAccessToken(user);
            String refreshToken = jwtUtil.generateRefreshToken(user);

            String userAgent = servletRequest.getHeader("User-Agent");
            String ipAddress = getClientIp(servletRequest);
            
            UserSession session = UserSession.builder()
                    .user(user)
                    .refreshToken(refreshToken)
                    .expiresAt(OffsetDateTime.now().plusSeconds(refreshTokenExpirationMs / 1000))
                    .userAgent(userAgent)
                    .ipAddress(ipAddress)
                    .build();
            userSessionRepository.save(session);
            
            return JwtResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .build();

        } catch (GeneralSecurityException | IOException e) {
            throw new BadRequestException("Xác thực Google thất bại: " + e.getMessage());
        }
    }

    private User registerNewUserFromGoogle(GoogleIdToken.Payload payload) {
        String email = payload.getEmail();
        String name = (String) payload.get("name");
        String pictureUrl = (String) payload.get("picture");

        User newUser = new User();
        newUser.setEmail(email);
        newUser.setPassword(passwordEncoder.encode(UUID.randomUUID().toString())); // Mật khẩu ngẫu nhiên
        newUser.setStatus(UserStatus.ACTIVE); 
        
        // --- (PHẦN SỬA ĐỔI QUAN TRỌNG) ---
        newUser.setAuthProvider("GOOGLE"); // Đánh dấu đây là tài khoản Google
        // --- (KẾT THÚC SỬA ĐỔI) ---

        Customer newCustomer = new Customer();
        newCustomer.setFullname(name);
        newCustomer.setPhoto(pictureUrl);
        newCustomer.setUser(newUser);
        newUser.setCustomer(newCustomer); 

        userRepository.save(newUser); 
        
        return newUser;
    }
    
    @Override
    public void forgotPassword(ForgotPasswordRequest request) {
        Optional<User> userOptional = userRepository.findByEmail(request.getEmail());

        if (userOptional.isPresent()) {
            User user = userOptional.get();

            PasswordResetToken resetToken = new PasswordResetToken(user);
            passwordResetTokenRepository.save(resetToken);

            // (Từ P1) Sửa link trỏ về Frontend
            String frontendUrl = "http://localhost:5173"; 
            String resetLink = frontendUrl + "/reset-password?token=" + resetToken.getToken();
            
            String emailBody = "<h1>Yêu cầu đặt lại mật khẩu</h1>" +
                               "<p>Bạn (hoặc ai đó) đã yêu cầu đặt lại mật khẩu cho tài khoản của bạn.</p>" +
                               "<p>Vui lòng nhấp vào liên kết dưới đây để đặt lại mật khẩu:</p>" +
                               "<a href=\"" + resetLink + "\">Đặt lại mật khẩu</a>" +
                               "<p>Liên kết này sẽ hết hạn trong 1 giờ. Nếu bạn không yêu cầu điều này, vui lòng bỏ qua email này.</p>";
            emailService.sendEmail(user.getEmail(), "Yêu cầu đặt lại mật khẩu", emailBody);
        }
    }

    @Override
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new BadRequestException("Token đặt lại mật khẩu không hợp lệ."));

        if (resetToken.isExpired()) {
            passwordResetTokenRepository.delete(resetToken);
            throw new BadRequestException("Token đặt lại mật khẩu đã hết hạn.");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        
        // --- (PHẦN SỬA ĐỔI QUAN TRỌNG) ---
        // Khi người dùng (bất kể là GOOGLE hay LOCAL) đặt lại mật khẩu,
        // tài khoản của họ giờ đây chính thức có mật khẩu do họ tự đặt.
        user.setAuthProvider("LOCAL"); 
        // --- (KẾT THÚC SỬA ĐỔI) ---
        
        userRepository.save(user);

        passwordResetTokenRepository.delete(resetToken);
    }
    
    @Override
    public void changePassword(ChangePasswordRequest request, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy người dùng đã được xác thực."));

        // --- (Ghi chú logic) ---
        // Logic này chỉ được gọi bởi người dùng "LOCAL" (vì frontend sẽ ẩn nút)
        // Nhưng để an toàn, ta có thể thêm kiểm tra
        if (!"LOCAL".equals(user.getAuthProvider())) {
            throw new BadRequestException("Tài khoản này không hỗ trợ đổi mật khẩu. Vui lòng sử dụng chức năng 'Tạo mật khẩu'.");
        }
        // --- (Kết thúc ghi chú) ---
        
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BadRequestException("Mật khẩu cũ không chính xác.");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }
    
    @Override
    @Transactional(readOnly = true) 
    public UserDetailsResponse getCurrentUserDetails(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với email: " + userEmail));

        // Sử dụng mapper thủ công của bạn
        return userMapper.toUserDetailsResponse(user);
    }
    
    @Override
    public void logout(String refreshToken) {
        UserSession session = userSessionRepository.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new BadRequestException("Refresh token không hợp lệ."));

        userSessionRepository.delete(session);
        
        userActivityLogService.logActivity("LOGOUT", null, session.getUser()); 
    }
}