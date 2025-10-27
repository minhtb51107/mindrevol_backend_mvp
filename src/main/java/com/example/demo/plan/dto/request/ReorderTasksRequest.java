package com.example.demo.plan.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat; // --- THÊM IMPORT ---
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull; // --- THÊM IMPORT ---
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate; // --- THÊM IMPORT ---
import java.util.List;

@Getter
@Setter
public class ReorderTasksRequest {

    @NotEmpty(message = "Danh sách ID công việc không được để trống")
    private List<Long> orderedTaskIds;

    // --- THÊM TRƯỜNG NÀY ---
    @NotNull(message = "Ngày của task không được để trống")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate taskDate;
    // --- KẾT THÚC THÊM ---
}