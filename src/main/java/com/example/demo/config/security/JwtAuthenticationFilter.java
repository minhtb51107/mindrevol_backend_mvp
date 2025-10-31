package com.example.demo.config.security;

import com.example.demo.shared.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        
        // === SỬA LỖI 401 WEBSOCKET ===
        // Lấy đường dẫn request
        final String requestURI = request.getRequestURI();

        // Nếu là request refresh-token HOẶC request WebSocket, bỏ qua bộ lọc
        if (requestURI.equals("/api/v1/auth/refresh-token") || requestURI.startsWith("/ws/")) {
            filterChain.doFilter(request, response);
            return;
        }
        // === KẾT THÚC SỬA LỖI ===

        // Bỏ qua nếu không có header Authorization hoặc không bắt đầu bằng "Bearer "
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);
        final String userEmail;
        
        try {
             userEmail = jwtUtil.getEmailFromToken(jwt);
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            // Đây là nơi log frontend "Received 401"
            // Chỉ cần cho request đi tiếp, CustomAuthenticationEntryPoint sẽ xử lý
            filterChain.doFilter(request, response);
            return;
        } catch (Exception e) {
             // Lỗi token khác (malformed, etc.)
//             log.warn("Invalid JWT Token: {}", e.getMessage());
             filterChain.doFilter(request, response);
             return;
        }


        // Nếu có email và chưa được xác thực trong SecurityContext
        if (StringUtils.hasText(userEmail) && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);

            // Nếu token hợp lệ, tạo đối tượng xác thực và đặt vào SecurityContext
            // (validateToken giờ chỉ kiểm tra tính hợp lệ chứ không kiểm tra hết hạn, vì getEmailFromToken đã làm)
            if (jwtUtil.validateToken(jwt)) { // Giả sử validateToken kiểm tra chữ ký
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null, // credentials
                        userDetails.getAuthorities()
                );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }
        filterChain.doFilter(request, response);
    }
}