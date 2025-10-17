package com.example.demo.user.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateCustomerRequest {

    @Size(min = 2, message = "Họ tên phải có ít nhất 2 ký tự")
    private String fullname;

    private String phoneNumber;

    private String photo;
}