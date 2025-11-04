// File: src/main/java/com/example/demo/user/dto/response/ProfileResponse.java
package com.example.demo.user.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder // Đảm bảo bạn có @Builder
public class ProfileResponse {
    private String id;
    private String email;
    private String status;
    private String userType;
    private String fullname;
    
    // ĐẢM BẢO TÊN TRƯỜNG LÀ 'photoUrl' (chữ 'U' viết hoa)
    private String photoUrl; 
    
    // ĐẢM BẢO BẠN CŨNG CÓ TRƯỜNG 'bio'
    private String bio; 
}