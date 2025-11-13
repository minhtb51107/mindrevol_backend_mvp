package com.example.demo.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class FriendRequestDto {
    @NotEmpty
    @Email
    private String email; // Người dùng sẽ gửi lời mời qua email
}