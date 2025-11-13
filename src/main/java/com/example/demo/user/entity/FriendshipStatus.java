package com.example.demo.user.entity;

public enum FriendshipStatus {
    PENDING,    // Đang chờ
    ACCEPTED,   // Đã là bạn
    DECLINED,   // Đã từ chối (hoặc BLOCK nếu muốn mở rộng sau này)
    CANCELLED   // Người gửi hủy lời mời
}
