package com.example.demo.user.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ProfileResponse {
    private Integer id;
    private String email;
    private String fullname;
    private String photoUrl; // Sử dụng photoUrl thay vì photo
    private String userType; // CUSTOMER or EMPLOYEE
    private String status;   // ACTIVE, PENDING_ACTIVATION, SUSPENDED
}