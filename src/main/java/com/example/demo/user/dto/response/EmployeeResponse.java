package com.example.demo.user.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.Set;

@Getter
@Setter
@Builder
public class EmployeeResponse {

    // --- Từ Employee entity ---
    private Integer id;
    private String employeeCode;
    private String fullname;
    private String position;
    private String department;
    private LocalDate hiredDate;
    private boolean isActive;

    // --- Từ User entity liên kết ---
    private String email;
    private String status;

    // --- Từ Role entity liên kết ---
    private Set<String> roleNames;
}