package com.example.demo.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateRoleRequest {
    @NotBlank(message = "Tên vai trò không được để trống")
    private String name;
    private String description;
}
