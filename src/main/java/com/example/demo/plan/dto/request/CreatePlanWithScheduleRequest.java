package com.example.demo.plan.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Getter
@Setter
public class CreatePlanWithScheduleRequest {
    // 1. Thông tin chung
    @NotBlank
    private String title;
    private String description;
    @Min(1)
    private int durationInDays;
    private String dailyGoal;
    @NotNull @FutureOrPresent
    private LocalDate startDate;
    
    private String motivation;

    // 2. Danh sách task đã được gán ngày cụ thể
    @Valid
    private List<ScheduledTaskRequest> tasks;

    @Getter
    @Setter
    public static class ScheduledTaskRequest {
        @NotBlank
        private String description;
        private LocalTime deadlineTime;
        @NotNull
        private LocalDate taskDate; // <-- Trường quan trọng
    }
}