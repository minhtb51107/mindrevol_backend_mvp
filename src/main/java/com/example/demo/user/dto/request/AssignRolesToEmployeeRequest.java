package com.example.demo.user.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import java.util.Set;

@Getter
@Setter
public class AssignRolesToEmployeeRequest {

    @NotEmpty(message = "Danh sách vai trò không được rỗng")
    private Set<String> roleNames; // Dùng tên vai trò (ví dụ: "SALE_MANAGER")
}