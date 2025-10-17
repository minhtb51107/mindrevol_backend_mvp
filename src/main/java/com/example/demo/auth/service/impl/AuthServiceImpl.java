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
    private final UserMapper userMapper; // <-- THÊM DEPENDENCY MỚI
    
    private final UserActivationTokenRepository activationTokenRepository;
    private final EmailService emailService;
    
    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String googleClientId;
    
    @Value("${app.jwt.refresh-token-expiration-ms}") 
    private long refreshTokenExpirationMs;
    
    @Value("${app.security.max-concurrent-sessions}")
    private int maxConcurrentSessions; // <-- BIẾN MỚI

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

        String activationLink = "http://localhost:8080/api/v1/auth/activate?token=" + activationToken.getToken();
        String emailBody = "<h1>Chào mừng bạn đến với Shop!</h1>" +
                           "<p>Vui lòng nhấp vào liên kết dưới đây để kích hoạt tài khoản của bạn:</p>" +
                           "<a href=\"" + activationLink + "\">Kích hoạt ngay</a>" +
                           "<p>Liên kết này sẽ hết hạn trong 24 giờ.</p>";
        emailService.sendEmail(user.getEmail(), "Kích hoạt tài khoản Shop", emailBody);
    }

    @Override
    public void activateUserAccount(String token) {
        // 1. Tìm token trong CSDL
        UserActivationToken activationToken = activationTokenRepository.findByToken(token)
                .orElseThrow(() -> new BadRequestException("Token kích hoạt không hợp lệ."));

        // 2. Kiểm tra token đã hết hạn chưa
        if (activationToken.isExpired()) {
            // (Tùy chọn) Có thể xóa user và token hết hạn tại đây để dọn dẹp
            activationTokenRepository.delete(activationToken);
            throw new BadRequestException("Token kích hoạt đã hết hạn.");
        }

        // 3. Lấy thông tin user và kích hoạt tài khoản
        User user = activationToken.getUser();
        if (user.getStatus() != UserStatus.PENDING_ACTIVATION) {
             throw new BadRequestException("Tài khoản này đã được kích hoạt trước đó.");
        }
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        // 4. Xóa token đã sử dụng
        activationTokenRepository.delete(activationToken);
    }

    @Override
    public JwtResponse login(LoginRequest request, HttpServletRequest servletRequest) { // <-- THAY ĐỔI CHỮ KÝ
        // 1. Xác thực bằng Spring Security (giữ nguyên)
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 2. Lấy thông tin user từ CSDL
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại."));

        // 3. *** KIỂM TRA TRẠNG THÁI USER (CẢI TIẾN BẢO MẬT) ***
        if (user.getStatus() != UserStatus.ACTIVE) {
            String message;
            if (user.getStatus() == UserStatus.PENDING_ACTIVATION) {
                message = "Tài khoản của bạn chưa được kích hoạt. Vui lòng kiểm tra email.";
            } else { // UserStatus.SUSPENDED
                message = "Tài khoản của bạn đã bị khóa. Vui lòng liên hệ quản trị viên.";
            }
            // Ném ra exception phù hợp. DisabledException sẽ trả về lỗi 401 Unauthorized.
            throw new DisabledException(message);
        }

        long sessionCount = userSessionRepository.countByUserId(user.getId());
        if (sessionCount >= maxConcurrentSessions) {
            // Tìm và xóa session cũ nhất
            userSessionRepository.findFirstByUserIdOrderByCreatedAtAsc(user.getId())
                    .ifPresent(userSessionRepository::delete);
        }

        // 4. Tạo access token và refresh token
        String accessToken = jwtUtil.generateAccessToken(user);
        String refreshToken = jwtUtil.generateRefreshToken(user);
        
        // 5. *** LƯU THÊM THÔNG TIN VÀO SESSION (CẢI TIẾN BẢO MẬT) ***
        String userAgent = servletRequest.getHeader("User-Agent");
        String ipAddress = getClientIp(servletRequest);
        
        UserSession session = UserSession.builder()
                .user(user)
                .refreshToken(refreshToken)
                .expiresAt(OffsetDateTime.now().plusSeconds(refreshTokenExpirationMs / 1000))
                .userAgent(userAgent) // <-- LƯU USER AGENT
                .ipAddress(ipAddress)   // <-- LƯU IP ADDRESS
                .build();
        userSessionRepository.save(session);

        return JwtResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    // Hàm tiện ích để lấy IP client, xử lý cả trường hợp có proxy
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
    
    @Override
    public JwtResponse refreshToken(String refreshToken) {
        // 1. Tìm session trong CSDL
        UserSession session = userSessionRepository.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new BadRequestException("Refresh token không hợp lệ."));

        // 2. Kiểm tra token đã hết hạn chưa
        if (session.getExpiresAt().isBefore(OffsetDateTime.now())) {
            userSessionRepository.delete(session); // Xóa token hết hạn
            throw new BadRequestException("Refresh token đã hết hạn.");
        }

        // 3. Lấy thông tin user
        User user = session.getUser();

        // 4. Tạo access token mới
        String newAccessToken = jwtUtil.generateAccessToken(user);

        // Trả về access token mới (refresh token giữ nguyên)
        return JwtResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken)
                .build();
    }
    
    @Override
    public JwtResponse loginWithGoogle(String idTokenString, HttpServletRequest servletRequest) { // <-- THAY ĐỔI CHỮ KÝ
        try {
            // 1. Xác thực ID Token với Google (giữ nguyên)
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                throw new BadRequestException("Token Google không hợp lệ.");
            }

            // 2. Lấy thông tin người dùng và tìm hoặc tạo mới (giữ nguyên)
            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail();
            User user = userRepository.findByEmail(email)
                    .orElseGet(() -> registerNewUserFromGoogle(payload));

            // *** BỎ COMMENT VÀ HOÀN THIỆN LOGIC LƯU REFRESH TOKEN (CẢI TIẾN MỚI) ***
            
            // 3. Kiểm tra giới hạn session
            long sessionCount = userSessionRepository.countByUserId(user.getId());
            if (sessionCount >= maxConcurrentSessions) {
                userSessionRepository.findFirstByUserIdOrderByCreatedAtAsc(user.getId())
                        .ifPresent(userSessionRepository::delete);
            }

            // 4. Tạo JWT token của hệ thống
            String accessToken = jwtUtil.generateAccessToken(user);
            String refreshToken = jwtUtil.generateRefreshToken(user);

            // 5. Lưu session vào CSDL
            String userAgent = servletRequest.getHeader("User-Agent");
            String ipAddress = getClientIp(servletRequest); // Tái sử dụng hàm tiện ích đã tạo
            
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

 // minhtb51107/shop_backend/shop_backend-integration/src/main/java/com/example/demo/user/service/impl/AuthServiceImpl.java

    private User registerNewUserFromGoogle(GoogleIdToken.Payload payload) {
        String email = payload.getEmail();
        String name = (String) payload.get("name");
        String pictureUrl = (String) payload.get("picture");

        // Tạo User mới
        User newUser = new User();
        newUser.setEmail(email);
        newUser.setPassword(passwordEncoder.encode(UUID.randomUUID().toString())); // Mật khẩu ngẫu nhiên
        newUser.setStatus(UserStatus.ACTIVE);

        // Tạo Customer tương ứng và liên kết hai chiều
        Customer newCustomer = new Customer();
        newCustomer.setFullname(name);
        newCustomer.setPhoto(pictureUrl);
        newCustomer.setUser(newUser);
        newUser.setCustomer(newCustomer); // <--- Thiết lập quan hệ hai chiều

        // *** SỬA Ở ĐÂY ***
        // Thay vì lưu customerRepository, hãy lưu userRepository
        userRepository.save(newUser); // Lưu user (customer sẽ được lưu theo nhờ cascade)
        
        return newUser;
    }
    
    @Override
    public void forgotPassword(ForgotPasswordRequest request) {
        // 1. Tìm user bằng email
        Optional<User> userOptional = userRepository.findByEmail(request.getEmail());

        // 2. Kể cả khi không tìm thấy user, chúng ta không báo lỗi.
        // Đây là một biện pháp bảo mật để tránh kẻ tấn công dò email hợp lệ.
        if (userOptional.isPresent()) {
            User user = userOptional.get();

            // 3. Tạo token reset
            PasswordResetToken resetToken = new PasswordResetToken(user);
            passwordResetTokenRepository.save(resetToken);

            // 4. Gửi email (bất đồng bộ)
            // Lưu ý: URL này nên được cấu hình trong application.properties
            String resetLink = "http://your-frontend-domain.com/reset-password?token=" + resetToken.getToken();
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
        // 1. Tìm token trong CSDL
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new BadRequestException("Token đặt lại mật khẩu không hợp lệ."));

        // 2. Kiểm tra token đã hết hạn chưa
        if (resetToken.isExpired()) {
            passwordResetTokenRepository.delete(resetToken);
            throw new BadRequestException("Token đặt lại mật khẩu đã hết hạn.");
        }

        // 3. Lấy thông tin user và cập nhật mật khẩu mới
        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // 4. Xóa token đã sử dụng
        passwordResetTokenRepository.delete(resetToken);
    }
    
    @Override
    public void changePassword(ChangePasswordRequest request, String userEmail) {
        // 1. Tìm người dùng trong CSDL
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy người dùng đã được xác thực."));

        // 2. Kiểm tra mật khẩu cũ có khớp không
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BadRequestException("Mật khẩu cũ không chính xác.");
        }

        // 3. Cập nhật mật khẩu mới
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }
    
    @Override
    @Transactional(readOnly = true) // Tối ưu cho việc đọc dữ liệu
    public UserDetailsResponse getCurrentUserDetails(String userEmail) {
        // 1. Tìm người dùng trong CSDL
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với email: " + userEmail));

        // 2. Dùng UserMapper để chuyển đổi Entity sang DTO
        // @Transactional sẽ giúp mapper có thể lazy-load các collection (roles, permissions)
        return userMapper.toUserDetailsResponse(user);
    }
    
    @Override
    public void logout(String refreshToken) {
        // Tìm session tương ứng với refresh token
        UserSession session = userSessionRepository.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new BadRequestException("Refresh token không hợp lệ."));

        // Xóa session này khỏi cơ sở dữ liệu
        userSessionRepository.delete(session);
        
        // (Tùy chọn) Ghi log hành động đăng xuất
        userActivityLogService.logActivity("LOGOUT", null, session.getUser()); 
        // -> Cần inject UserActivityLogService nếu muốn làm điều này.
    }
}