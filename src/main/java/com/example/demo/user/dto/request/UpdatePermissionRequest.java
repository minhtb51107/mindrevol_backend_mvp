package com.example.demo.user.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdatePermissionRequest {
    // Chỉ cho phép cập nhật mô tả, không cho cập nhật tên quyền
    private String description;
}