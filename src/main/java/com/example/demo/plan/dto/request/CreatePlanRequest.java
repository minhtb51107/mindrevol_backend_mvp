package com.example.demo.plan.dto.request;

import java.time.LocalDate;
import java.util.List; // Thêm import này
import java.util.ArrayList; // Thêm import này

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreatePlanRequest {

    @NotBlank(message = "Tiêu đề không được để trống")
    private String title;

    private String description;

    @NotNull(message = "Thời lượng không được để trống")
    @Min(value = 1, message = "Thời lượng phải ít nhất là 1 ngày")
    private Integer durationInDays;

    @NotNull(message = "Ngày bắt đầu không được để trống")
    @JsonFormat(pattern="yyyy-MM-dd")
    private LocalDate startDate;

    private String dailyGoal;

    // --- THÊM PHẦN NÀY ---
    private List<String> dailyTasks = new ArrayList<>(); // Cho phép danh sách rỗng
    // --- KẾT THÚC PHẦN THÊM ---
}