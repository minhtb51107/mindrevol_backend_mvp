package com.example.demo.feed.dto;

import com.example.demo.feed.entity.FeedEventType; // Import Enum
import com.fasterxml.jackson.databind.JsonNode; // Sử dụng JsonNode để linh hoạt hơn
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
public class FeedEventDto {
    private Long id;
    private FeedEventType eventType; // Sử dụng Enum
    private OffsetDateTime timestamp;

    // Thông tin người thực hiện (chỉ ID và tên)
    private Integer actorId;
    private String actorFullName;

    // Thông tin kế hoạch (chỉ ID và tiêu đề)
    private Integer planId;
    private String planTitle;

    // Chi tiết sự kiện (dạng JSON object)
    private JsonNode details; // Sử dụng JsonNode thay vì String
}