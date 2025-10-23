package com.example.demo.config.security;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.example.demo.shared.util.JwtUtil; // Import JwtUtil

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        // Chỉ xử lý khi client gửi lệnh CONNECT
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            log.debug("Intercepting STOMP CONNECT message");

            // Lấy token từ header 'Authorization' (mà client đã gửi trong connectHeaders)
            String authorizationHeader = accessor.getFirstNativeHeader("Authorization");
            log.debug("Authorization header: {}", authorizationHeader);

            if (StringUtils.hasText(authorizationHeader) && authorizationHeader.startsWith("Bearer ")) {
                String jwt = authorizationHeader.substring(7);

                try {
                    if (jwtUtil.validateToken(jwt)) {
                        String username = jwtUtil.getEmailFromToken(jwt); // Lấy email (username)
                        log.debug("JWT validated for user: {}", username);

                        // Load user details từ UserDetailsService
                        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                        // Tạo đối tượng Authentication
                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());

                        // *** Quan trọng: Đặt Principal vào header để Spring Security WebSocket nhận biết ***
                        accessor.setUser(authentication);
                        log.info("Authenticated WebSocket connection for user: {}", username);
                        // SecurityContextHolder.getContext().setAuthentication(authentication); // Không cần set vào context ở đây
                    } else {
                         log.warn("Invalid JWT token received in WebSocket CONNECT header.");
                         // Có thể ném exception ở đây để từ chối kết nối nếu muốn
                         // throw new AuthenticationCredentialsNotFoundException("Invalid JWT token");
                    }
                } catch (Exception e) {
                    log.error("Error authenticating WebSocket connection: {}", e.getMessage());
                     // Có thể ném exception ở đây
                     // throw new AuthenticationCredentialsNotFoundException("Authentication failed", e);
                }
            } else {
                 log.warn("Missing or invalid Authorization header in WebSocket CONNECT message.");
                 // Có thể ném exception ở đây
                 // throw new AuthenticationCredentialsNotFoundException("Missing Authorization header");
            }
        }
        return message; // Luôn trả về message để tiếp tục xử lý
    }
}