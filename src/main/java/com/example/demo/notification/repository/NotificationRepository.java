package com.example.demo.notification.repository;

import com.example.demo.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying; // Thêm import
import org.springframework.data.jpa.repository.Query; // Thêm import
import org.springframework.data.repository.query.Param; // Thêm import
import org.springframework.stereotype.Repository;


@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findByRecipientIdOrderByCreatedAtDesc(Integer recipientId, Pageable pageable);

    // --- THÊM PHƯƠNG THỨC NÀY ---
    @Modifying // Đánh dấu đây là query thay đổi dữ liệu
    @Query("UPDATE Notification n SET n.read = true WHERE n.recipient.id = :recipientId AND n.read = false")
    int markAllAsReadForRecipient(@Param("recipientId") Integer recipientId);
    // --- KẾT THÚC THÊM ---
}