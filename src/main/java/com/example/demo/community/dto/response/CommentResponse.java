package com.example.demo.community.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

// import java.time.LocalDateTime; // Đổi sang Instant để tương thích JSON/JS
import java.time.Instant; 

@Getter
@Setter
@Builder
public class CommentResponse {
    private Long id;
    private String content;
    private String authorEmail;
    private String authorFullName;
    
    // --- THÊM CÁC TRƯỜNG SAU ---
    
    // Cần thiết cho logic Sửa/Xóa ở Frontend (CommentSection.vue)
    private Integer authorId; 

    // Cần thiết để Frontend hiển thị "5 phút trước" (CommentSection.vue)
    private Instant createdAt;
    
    // Thêm trường này để Frontend biết comment được liên kết với CheckInEvent nào
    private Long checkInEventId; 
}