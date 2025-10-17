package com.example.demo.user.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import java.util.Set;

@Getter
@Setter
public class AssignPermissionToRoleRequest {

    @NotEmpty(message = "Danh sách quyền không được rỗng")
    private Set<String> permissionNames; // Dùng tên quyền (ví dụ: "UPDATE_PRODUCT")
}