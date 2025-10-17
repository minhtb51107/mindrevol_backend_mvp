package com.example.demo.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class JwtResponse {
    private String accessToken;
    private String refreshToken;
    @Builder.Default // <-- THÊM ANNOTATION NÀY
    private String tokenType = "Bearer";
}