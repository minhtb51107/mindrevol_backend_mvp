package com.example.demo.user.mapper;

import com.example.demo.user.dto.response.UserDetailsResponse;
import com.example.demo.user.entity.Employee;
import com.example.demo.user.entity.Permission;
import com.example.demo.user.entity.User;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class UserMapper {

    public UserDetailsResponse toUserDetailsResponse(User user) {
        if (user == null) {
            return null;
        }

        String fullname = null;
        String userType = "CUSTOMER"; // Mặc định là Customer
        Set<String> roles;
        Set<String> permissions;

        Employee employee = user.getEmployee();
        if (employee != null) {
            userType = "EMPLOYEE";
            fullname = employee.getFullname();
            roles = employee.getRoles().stream()
                    .map(role -> "ROLE_" + role.getName())
                    .collect(Collectors.toSet());
            permissions = employee.getRoles().stream()
                    .flatMap(role -> role.getPermissions().stream())
                    .map(Permission::getName)
                    .collect(Collectors.toSet());
        } else {
            // Nếu là Customer
            if (user.getCustomer() != null) {
                fullname = user.getCustomer().getFullname();
            }
            roles = Collections.singleton("ROLE_CUSTOMER");
            permissions = Collections.emptySet();
        }

        // --- (PHẦN SỬA ĐỔI) ---
        // Sử dụng builder từ DTO của bạn
        return UserDetailsResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullname(fullname)
                .userType(userType)
                .roles(roles)
                .permissions(permissions)
                .authProvider(user.getAuthProvider()) // Thêm trường mới
                .build();
        // --- (KẾT THÚC SỬA ĐỔI) ---
    }
}