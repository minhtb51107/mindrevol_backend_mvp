package com.example.demo.progress.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
public class CheckInRequest {

    // Ngày sẽ được server ghi nhận tự động (timestamp), không cần gửi lên

    private String notes; // Ghi chú chung cho lần check-in này

    @Valid
    private List<AttachmentRequest> attachments = new ArrayList<>(); // Ảnh/file đính kèm

    private Set<Long> completedTaskIds = new HashSet<>(); // Danh sách ID task hoàn thành

    // Inner class cho thông tin attachment
    @Getter
    @Setter
    public static class AttachmentRequest {
        @NotBlank
        private String storedFilename;
        @NotBlank
        private String fileUrl;
        private String originalFilename;
        private String contentType;
        private Long fileSize;
    }
}