package com.example.demo.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import com.example.demo.auth.entity.UserSession;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    /**
     * Tìm một session bằng refresh token. Đây là phương thức cốt lõi.
     * @param refreshToken Chuỗi refresh token.
     * @return Optional chứa UserSession nếu tìm thấy.
     */
    Optional<UserSession> findByRefreshToken(String refreshToken);

    /**
     * Xóa tất cả các session đã hết hạn.
     * Dùng để dọn dẹp CSDL định kỳ.
     * @param now Thời gian hiện tại.
     */
    @Modifying // Báo cho Spring biết đây là một query thay đổi dữ liệu (DELETE/UPDATE)
    void deleteAllByExpiresAtBefore(OffsetDateTime now);
    
    // --- CÁC PHƯƠNG THỨC MỚI ĐƯỢC THÊM VÀO ---

    /**
     * Đếm số lượng session đang hoạt động của một người dùng.
     * @param userId ID của người dùng.
     * @return Số lượng session.
     */
    long countByUserId(Integer userId);

    /**
     * Tìm session cũ nhất (dựa trên thời gian tạo) của một người dùng.
     * @param userId ID của người dùng.
     * @return Optional chứa session cũ nhất nếu có.
     */
    Optional<UserSession> findFirstByUserIdOrderByCreatedAtAsc(Integer userId);
}