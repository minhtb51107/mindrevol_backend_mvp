package com.example.demo.progress.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank; // Đảm bảo import NotBlank
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;

@Getter
@Setter
public class LogProgressRequest {

    @NotNull(message = "Ngày không được để trống")
    private LocalDate date;

    @NotNull(message = "Trạng thái hoàn thành không được để trống")
    private Boolean completed; // Trạng thái hoàn thành chung của ngày

    private String notes; // Ghi chú chung cho ngày

    private List<String> evidence = new ArrayList<>(); // Link chung cho ngày

    private Set<Long> completedTaskIds = new HashSet<>(); // Danh sách ID task hoàn thành

    // --- BỎ TRƯỜNG NÀY ---
    // @Valid
    // private List<AttachmentRequest> attachments = new ArrayList<>(); // Bỏ danh sách attachment chung
    // --- KẾT THÚC BỎ ---

    // --- THÊM TRƯỜNG NÀY ---
    @Valid // Validate các TaskCheckinUpdateRequest bên trong
    private List<TaskCheckinUpdateRequest> taskUpdates = new ArrayList<>(); // Danh sách cập nhật cho từng task
    // --- KẾT THÚC THÊM ---


    // Inner class AttachmentRequest giữ nguyên để TaskCheckinUpdateRequest sử dụng
    @Getter
    @Setter
    public static class AttachmentRequest {
        @NotBlank(message = "Stored filename không được trống")
        private String storedFilename;

        @NotBlank(message = "File URL không được trống")
        private String fileUrl;

        private String originalFilename;
        private String contentType;
        private Long fileSize;
    }
}