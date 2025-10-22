package com.example.demo.plan.dto.request;

import jakarta.validation.Valid; // Thêm import
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size; // Thêm import
import lombok.Getter;
import lombok.Setter;
import java.time.LocalTime; // Thêm import
import com.fasterxml.jackson.annotation.JsonFormat; // Thêm import
import java.util.List;
import java.util.ArrayList;

@Getter
@Setter
public class UpdatePlanRequest {

    @NotBlank(message = "Tiêu đề không được để trống")
    private String title;

    private String description;

    @Min(value = 1, message = "Thời lượng phải ít nhất là 1 ngày")
    private Integer durationInDays;

    private String dailyGoal;

    @Valid // Thêm Valid
    private List<TaskRequest> dailyTasks = new ArrayList<>();

    // --- THÊM INNER CLASS TaskRequest (giống CreatePlanRequest) ---
    @Getter
    @Setter
    public static class TaskRequest {
        @NotBlank(message = "Mô tả công việc không được để trống")
        @Size(max = 1000, message = "Mô tả công việc quá dài")
        private String description;

        @JsonFormat(pattern = "HH:mm")
        private LocalTime deadlineTime; // Nullable
    }
    // --- KẾT THÚC THÊM ---
}