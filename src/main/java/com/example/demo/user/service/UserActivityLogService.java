package com.example.demo.user.service;

import org.springframework.data.domain.Pageable; // Sửa import này
import java.time.OffsetDateTime;

import org.springframework.data.domain.Page;

import com.example.demo.user.dto.response.UserActivityLogResponse;
import com.example.demo.user.entity.User;

public interface UserActivityLogService {
    
    /**
     * Ghi lại một hành động của người dùng cụ thể.
     * @param action Mô tả hành động (ví dụ: "LOGIN", "CREATE_PRODUCT").
     * @param details Chi tiết hành động (có thể là đối tượng được chuyển thành JSON).
     * @param user Người dùng thực hiện hành động.
     */
    void logActivity(String action, Object details, User user);

    /**
     * Lấy lịch sử hoạt động có phân trang.
     * @param pageable Thông tin phân trang.
     * @return Một trang (Page) chứa danh sách log.
     */
	Page<UserActivityLogResponse> getActivityLogs(Pageable pageable);

	Page<UserActivityLogResponse> searchLogs(Integer userId, String action, OffsetDateTime startDate,
			OffsetDateTime endDate, Pageable pageable);
}