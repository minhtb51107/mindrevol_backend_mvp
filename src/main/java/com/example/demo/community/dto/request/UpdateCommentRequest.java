package com.example.demo.community.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateCommentRequest {
    @NotBlank(message = "Nội dung bình luận không được để trống")
    private String content;
}