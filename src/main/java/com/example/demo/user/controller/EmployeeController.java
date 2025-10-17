package com.example.demo.user.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.user.dto.request.AssignRolesToEmployeeRequest;
import com.example.demo.user.dto.request.CreateEmployeeRequest;
import com.example.demo.user.dto.request.UpdateEmployeeRequest;
import com.example.demo.user.dto.response.EmployeeResponse;
import com.example.demo.user.service.EmployeeService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')") // Chỉ Admin được tạo nhân viên
    public ResponseEntity<EmployeeResponse> createEmployee(@Valid @RequestBody CreateEmployeeRequest request) {
        EmployeeResponse newEmployee = employeeService.createEmployee(request);
        return new ResponseEntity<>(newEmployee, HttpStatus.CREATED);
    }
    
 // --- PHƯƠNG THỨC BỔ SUNG ---
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EmployeeResponse> getEmployeeById(@PathVariable Integer id) {
        // Cần thêm phương thức getEmployeeById(id) vào EmployeeService
        EmployeeResponse employee = employeeService.getEmployeeById(id);
        return ResponseEntity.ok(employee);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<EmployeeResponse>> getAllEmployees(Pageable pageable) {
        // Cần thêm phương thức getAllEmployees(pageable) vào EmployeeService
        Page<EmployeeResponse> employees = employeeService.getAllEmployees(pageable);
        return ResponseEntity.ok(employees);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EmployeeResponse> updateEmployee(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateEmployeeRequest request) {
        // Cần thêm phương thức updateEmployee(id, request) vào EmployeeService
        EmployeeResponse updatedEmployee = employeeService.updateEmployee(id, request);
        return ResponseEntity.ok(updatedEmployee);
    }
    
    @PatchMapping("/{id}/status") // Dùng PATCH cho việc cập nhật một phần
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateEmployeeStatus(
            @PathVariable Integer id,
            @RequestParam boolean isActive) {
        // Cần thêm phương thức updateEmployeeStatus(id, isActive) vào EmployeeService
        employeeService.updateEmployeeStatus(id, isActive);
        return ResponseEntity.ok("Cập nhật trạng thái nhân viên thành công.");
    }
    
    @PostMapping("/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EmployeeResponse> assignRolesToEmployee(
            @PathVariable("id") Integer employeeId,
            @Valid @RequestBody AssignRolesToEmployeeRequest request) {
        EmployeeResponse updatedEmployee = employeeService.assignRolesToEmployee(employeeId, request);
        return ResponseEntity.ok(updatedEmployee);
    }
}