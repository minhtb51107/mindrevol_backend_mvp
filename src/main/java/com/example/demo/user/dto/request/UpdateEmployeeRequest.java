package com.example.demo.user.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateEmployeeRequest {
    
    @Size(min = 2, message = "Họ tên phải có ít nhất 2 ký tự")
    private String fullname;

    private String position;

    private String department;
    
    // Email và employeeCode thường không được phép thay đổi
}
