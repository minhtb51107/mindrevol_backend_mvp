package com.example.demo.user.service.impl;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.shared.exception.BadRequestException;
import com.example.demo.shared.exception.ResourceNotFoundException;
import com.example.demo.user.dto.request.AssignRolesToEmployeeRequest;
import com.example.demo.user.dto.request.CreateEmployeeRequest;
import com.example.demo.user.dto.request.UpdateEmployeeRequest;
import com.example.demo.user.dto.response.EmployeeResponse;
import com.example.demo.user.entity.Employee;
import com.example.demo.user.entity.Role;
import com.example.demo.user.entity.User;
import com.example.demo.user.entity.UserStatus;
import com.example.demo.user.mapper.EmployeeMapper;
import com.example.demo.user.repository.EmployeeRepository;
import com.example.demo.user.repository.RoleRepository;
import com.example.demo.user.repository.UserRepository;
import com.example.demo.user.service.EmployeeService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final EmployeeMapper employeeMapper;
    private final PasswordEncoder passwordEncoder;

 // Version đã sửa lỗi
    @Override
    public EmployeeResponse createEmployee(CreateEmployeeRequest request) {
        // 1. Validate (Giữ nguyên)
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email đã được sử dụng.");
        }
        
        // 2. Tìm các Role từ CSDL (Giữ nguyên)
        Set<Role> roles = request.getRoleNames().stream()
                .map(roleName -> roleRepository.findByName(roleName)
                        .orElseThrow(() -> new BadRequestException("Vai trò không hợp lệ: " + roleName)))
                .collect(Collectors.toSet());

        // 3. Tạo User (Giữ nguyên)
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setStatus(UserStatus.ACTIVE);

        // 4. Tạo Employee (Giữ nguyên)
        Employee employee = employeeMapper.toEmployeeEntity(request);
        
        // 5. *** THAY ĐỔI QUAN TRỌNG: Thiết lập quan hệ 2 chiều ***
        employee.setUser(user);      // Employee biết về User
        employee.setRoles(roles);    // Employee biết về Roles
        user.setEmployee(employee);  // User cũng phải biết về Employee

        // 6. *** THAY ĐỔI QUAN TRỌNG: Lưu User thay vì Employee ***
        User savedUser = userRepository.save(user);

        // 7. *** THAY ĐỔI QUAN TRỌNG: Trả về response từ đối tượng đã lưu ***
        return employeeMapper.toEmployeeResponse(savedUser.getEmployee());
    }
    
    @Override
    @Transactional(readOnly = true)
    public EmployeeResponse getEmployeeById(Integer id) {
        return employeeRepository.findById(id)
                .map(employeeMapper::toEmployeeResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân viên với ID: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EmployeeResponse> getAllEmployees(Pageable pageable) {
        return employeeRepository.findAll(pageable)
                .map(employeeMapper::toEmployeeResponse);
    }

    @Override
    public EmployeeResponse updateEmployee(Integer id, UpdateEmployeeRequest request) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân viên với ID: " + id));

        if (request.getFullname() != null) {
            employee.setFullname(request.getFullname());
        }
        if (request.getPosition() != null) {
            employee.setPosition(request.getPosition());
        }
        if (request.getDepartment() != null) {
            employee.setDepartment(request.getDepartment());
        }
        
        Employee updatedEmployee = employeeRepository.save(employee);
        return employeeMapper.toEmployeeResponse(updatedEmployee);
    }

    @Override
    public void updateEmployeeStatus(Integer id, boolean isActive) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân viên với ID: " + id));
        employee.setActive(isActive);
        employeeRepository.save(employee);
    }
    
    @Override
    public EmployeeResponse assignRolesToEmployee(Integer employeeId, AssignRolesToEmployeeRequest request) {
        // 1. Tìm nhân viên trong CSDL
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân viên với ID: " + employeeId));

        // 2. Tìm tất cả các đối tượng Role hợp lệ từ danh sách tên vai trò
        Set<Role> roles = request.getRoleNames().stream()
                .map(roleName -> roleRepository.findByName(roleName)
                        .orElseThrow(() -> new BadRequestException("Tên vai trò không hợp lệ: " + roleName)))
                .collect(Collectors.toSet());

        // 3. Cập nhật lại danh sách vai trò cho nhân viên
        employee.setRoles(roles);

        // 4. Lưu lại thông tin nhân viên đã được cập nhật
        Employee updatedEmployee = employeeRepository.save(employee);

        // 5. Trả về thông tin nhân viên dưới dạng DTO
        return employeeMapper.toEmployeeResponse(updatedEmployee);
    }
}