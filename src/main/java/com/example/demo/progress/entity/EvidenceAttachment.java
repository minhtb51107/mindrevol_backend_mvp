package com.example.demo.progress.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "evidence_attachments")
public class EvidenceAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "daily_progress_id", nullable = false)
    private DailyProgress dailyProgress; // Liên kết về DailyProgress

    @Column(name = "original_filename", nullable = false)
    private String originalFilename; // Tên file gốc người dùng upload

    @Column(name = "stored_filename", nullable = false, unique = true)
    private String storedFilename; // Tên file lưu trên server (thường là duy nhất)

    @Column(name = "file_path", nullable = false, columnDefinition = "TEXT")
    private String filePath; // Đường dẫn tương đối hoặc tuyệt đối tới file

    @Column(name = "file_url", columnDefinition = "TEXT") // URL để truy cập file (nếu có)
    private String fileUrl;

    @Column(name = "content_type")
    private String contentType; // Loại file (e.g., image/jpeg, application/pdf)

    @Column(name = "file_size")
    private Long fileSize; // Kích thước file (bytes)

    @Builder.Default
    @Column(name = "uploaded_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime uploadedAt = OffsetDateTime.now();

}