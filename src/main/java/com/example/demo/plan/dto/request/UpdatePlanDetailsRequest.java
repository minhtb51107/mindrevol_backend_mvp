package com.example.demo.plan.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePlanDetailsRequest {

    @NotBlank(message = "Tên kế hoạch không được để trống")
    @Size(max = 100, message = "Tên kế hoạch quá dài (tối đa 100 ký tự)")
    private String title;

    @Size(max = 500, message = "Mô tả quá dài (tối đa 500 ký tự)")
    private String description;

    @Size(max = 100, message = "Mục tiêu hàng ngày quá dài (tối đa 100 ký tự)")
    private String dailyGoal;
}