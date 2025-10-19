package com.example.demo.plan.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import java.util.List; // Thêm import này
import java.util.ArrayList; // Thêm import này

@Getter
@Setter
public class UpdatePlanRequest {

    @NotBlank(message = "Tiêu đề không được để trống")
    private String title;

    private String description;

    @Min(value = 1, message = "Thời lượng phải ít nhất là 1 ngày")
    private Integer durationInDays;

    private String dailyGoal;

    // --- THÊM PHẦN NÀY ---
    private List<String> dailyTasks = new ArrayList<>(); // Cho phép danh sách rỗng
    // --- KẾT THÚC PHẦN THÊM ---
}