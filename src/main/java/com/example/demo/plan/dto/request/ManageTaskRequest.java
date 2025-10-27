package com.example.demo.plan.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate; // --- THÊM IMPORT ---
import java.time.LocalTime;

@Getter
@Setter
public class ManageTaskRequest {

    @NotBlank(message = "Mô tả công việc không được để trống")
    @Size(max = 1000, message = "Mô tả công việc quá dài")
    private String description;

    // Format HH:mm (ví dụ: "17:30") khi gửi từ frontend
    @JsonFormat(pattern = "HH:mm")
    private LocalTime deadlineTime; // Nullable

    // --- THÊM TRƯỜNG NÀY ---
    // Format YYYY-MM-DD (ví dụ: "2025-10-28")
    // Sẽ là bắt buộc khi tạo task mới
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate taskDate; 
    // --- KẾT THÚC THÊM ---
}