package com.example.demo.plan.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdatePlanRequest {

    @NotBlank(message = "Tiêu đề không được để trống")
    private String title;

    private String description;

    @Min(value = 1, message = "Thời lượng phải ít nhất là 1 ngày")
    private Integer durationInDays;

    private String dailyGoal;
}