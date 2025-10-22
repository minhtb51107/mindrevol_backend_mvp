package com.example.demo.plan.repository;

import com.example.demo.plan.entity.TaskAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List; // Thêm import

@Repository
public interface TaskAttachmentRepository extends JpaRepository<TaskAttachment, Long> {
    List<TaskAttachment> findByTaskIdOrderByUploadedAtAsc(Long taskId); // Tìm attachment theo Task ID
    // Optional<TaskAttachment> findByStoredFilename(String storedFilename); // Tìm theo tên file lưu trữ nếu cần
}