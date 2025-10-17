package com.example.demo.user.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.example.demo.user.entity.UserActivityLog;

import java.time.OffsetDateTime;

@Repository
public interface UserActivityLogRepository extends JpaRepository<UserActivityLog, Long>, JpaSpecificationExecutor<UserActivityLog> {

    /**
     * Tìm kiếm và phân trang tất cả log của một người dùng cụ thể.
     * @param userId ID của người dùng.
     * @param pageable Thông tin phân trang (trang số mấy, bao nhiêu item mỗi trang).
     * @return Một trang (Page) chứa danh sách log.
     */
    Page<UserActivityLog> findByUser_Id(Integer userId, Pageable pageable);

    /**
     * Tìm kiếm và phân trang tất cả log trong một khoảng thời gian.
     * @param start Thời gian bắt đầu.
     * @param end Thời gian kết thúc.
     * @param pageable Thông tin phân trang.
     * @return Một trang (Page) chứa danh sách log.
     */
    Page<UserActivityLog> findByCreatedAtBetween(OffsetDateTime start, OffsetDateTime end, Pageable pageable);
}