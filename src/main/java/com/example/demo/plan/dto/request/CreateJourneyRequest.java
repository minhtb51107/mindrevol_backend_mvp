package com.example.demo.plan.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreateJourneyRequest {

    @NotEmpty(message = "Tên hành trình không được để trống")
    @Size(max = 100)
    private String title;

    @Size(max = 500)
    private String description;

    @NotEmpty(message = "Lý do (motivation) không được để trống")
    @Size(max = 1000)
    private String motivation;

    // Danh sách ID của bạn bè (từ Phần 1) để mời
    private List<Integer> friendIds;
}