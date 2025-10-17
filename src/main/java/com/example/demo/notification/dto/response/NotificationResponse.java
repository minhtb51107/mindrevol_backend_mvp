package com.example.demo.notification.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
public class NotificationResponse {
    private Long id;
    private String message;
    private boolean read;
    private String link;
    private OffsetDateTime createdAt;
}