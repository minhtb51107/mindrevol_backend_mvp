package com.example.demo.user.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder // Dùng Builder để dễ dàng map từ User entity
public class FriendResponseDto {
    private Long friendshipId; // ID của quan hệ (dùng để chấp nhận/từ chối)
    private Long userId;       // ID của người bạn/người gửi
    private String fullName;
    private String email;
    private String photoUrl;
    // Thêm bất cứ trường nào bạn muốn hiển thị trên UI
}