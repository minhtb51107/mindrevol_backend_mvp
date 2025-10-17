package com.example.demo.user.mapper;

import com.example.demo.user.dto.response.UserDetailsResponse;
import com.example.demo.user.entity.Employee;
import com.example.demo.user.entity.Permission;
import com.example.demo.user.entity.Role;
import com.example.demo.user.entity.User;
import org.springframework.stereotype.Component;
import java.util.Collections;
import java.util.stream.Collectors;

@Component
public class UserMapper {

    public UserDetailsResponse toUserDetailsResponse(User user) {
        if (user == null) return null;

        UserDetailsResponse.UserDetailsResponseBuilder builder = UserDetailsResponse.builder()
                .id(user.getId())
                .email(user.getEmail());

        if (user.getCustomer() != null) {
            builder.fullname(user.getCustomer().getFullname())
                   .userType("CUSTOMER");
        } else if (user.getEmployee() != null) {
            Employee employee = user.getEmployee();
            builder.fullname(employee.getFullname())
                   .userType("EMPLOYEE");

            if (employee.getRoles() != null) {
                builder.roles(employee.getRoles().stream()
                                .map(Role::getName)
                                .collect(Collectors.toSet()));

                builder.permissions(employee.getRoles().stream()
                                .flatMap(role -> role.getPermissions().stream())
                                .map(Permission::getName)
                                .collect(Collectors.toSet()));
            } else {
                 builder.roles(Collections.emptySet());
                 builder.permissions(Collections.emptySet());
            }
        }
        return builder.build();
    }
}