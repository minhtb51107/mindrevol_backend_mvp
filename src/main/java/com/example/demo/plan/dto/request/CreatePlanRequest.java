package com.example.demo.plan.dto.request;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.ArrayList;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreatePlanRequest {

    @NotBlank(message = "Tiêu đề không được để trống")
    private String title;

    private String description;

    // --- THÊM TRƯỜNG MỚI ---
    private String motivation;
    // ------------------------

    @NotNull(message = "Thời lượng không được để trống")
    @Min(value = 1, message = "Thời lượng phải ít nhất là 1 ngày")
    private Integer durationInDays;

    @NotNull(message = "Ngày bắt đầu không được để trống")
    @JsonFormat(pattern="yyyy-MM-dd")
    private LocalDate startDate;

    private String dailyGoal;

    @Valid
    private List<TaskRequest> dailyTasks = new ArrayList<>();
    
    private boolean repeatTasks;

    @Getter
    @Setter
    public static class TaskRequest {
        @NotBlank(message = "Mô tả công việc không được để trống")
        @Size(max = 1000, message = "Mô tả công việc quá dài")
        private String description;

        @JsonFormat(pattern = "HH:mm")
        private LocalTime deadlineTime;
    }
}