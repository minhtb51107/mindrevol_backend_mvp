package com.example.demo.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreatePermissionRequest {

    @NotBlank(message = "Tên quyền không được để trống")
    private String name; // Ví dụ: "CREATE_PRODUCT", "VIEW_REPORT"

    private String description;
}