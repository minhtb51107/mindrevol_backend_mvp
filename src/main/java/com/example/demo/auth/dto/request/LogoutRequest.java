package com.example.demo.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LogoutRequest {

    @NotBlank(message = "Refresh token không được để trống")
    private String refreshToken;
}