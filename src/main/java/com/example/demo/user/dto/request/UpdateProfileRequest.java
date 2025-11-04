// file: UpdateProfileRequest.java
package com.example.demo.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateProfileRequest {

    @NotBlank(message = "Họ tên không được để trống")
    @Size(min = 2, message = "Họ tên phải có ít nhất 2 ký tự")
    private String fullname;

    // Cho phép cập nhật URL ảnh đại diện (tùy chọn)
    private String photoUrl;

    // --- THÊM TRƯỜNG NÀY ---
    @Size(max = 200, message = "Giới thiệu không được quá 200 ký tự")
    private String bio;
}