package com.example.demo.user.service;

import com.example.demo.user.dto.request.AssignRolesToEmployeeRequest; // Thêm import này
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.example.demo.user.dto.request.CreateEmployeeRequest;
import com.example.demo.user.dto.request.UpdateEmployeeRequest;
import com.example.demo.user.dto.response.EmployeeResponse;

public interface EmployeeService {

    /**
     * Tạo mới một nhân viên.
     * @param request DTO chứa thông tin nhân viên mới.
     * @return DTO chứa thông tin nhân viên vừa tạo.
     */
    EmployeeResponse createEmployee(CreateEmployeeRequest request);
    
    EmployeeResponse getEmployeeById(Integer id);
    
    Page<EmployeeResponse> getAllEmployees(Pageable pageable);
    
    EmployeeResponse updateEmployee(Integer id, UpdateEmployeeRequest request);
    
    void updateEmployeeStatus(Integer id, boolean isActive);

    /**
     * Gán hoặc cập nhật danh sách vai trò cho một nhân viên.
     * @param employeeId ID của nhân viên cần gán vai trò.
     * @param request DTO chứa danh sách tên các vai trò.
     * @return DTO của nhân viên sau khi đã được cập nhật vai trò.
     */
    EmployeeResponse assignRolesToEmployee(Integer employeeId, AssignRolesToEmployeeRequest request); // <-- THÊM PHƯƠNG THỨC NÀY
}