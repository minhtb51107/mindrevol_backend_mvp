// Đường dẫn: main/java/com/example/demo/progress/dto/response/MemberJourneyProgressDto.java
package com.example.demo.progress.dto.response;

import lombok.Data;
// Không cần UUID nữa

@Data
public class MemberJourneyProgressDto {
    private Integer userId; // Sửa thành Integer để khớp với User.java
    private String fullName;
    private String photoUrl; // Sẽ map từ trường 'photo' của User.java
    private int currentDay; 
}