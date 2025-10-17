package com.example.demo.user.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.util.Set;

@Getter
@Setter
@Builder
public class UserDetailsResponse {
    private Integer id;
    private String email;
    private String fullname;
    private String userType; // "CUSTOMER" hoặc "EMPLOYEE"
    private Set<String> roles;
    private Set<String> permissions;
};