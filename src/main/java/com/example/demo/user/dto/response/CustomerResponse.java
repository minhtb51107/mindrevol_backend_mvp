package com.example.demo.user.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class CustomerResponse {
    private Integer id;
    private String fullname;
    private String email;
    private String phoneNumber;
    private String photo;
    private String status;
}