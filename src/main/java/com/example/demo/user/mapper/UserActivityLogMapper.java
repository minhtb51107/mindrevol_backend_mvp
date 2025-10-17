package com.example.demo.user.mapper;

import com.example.demo.user.dto.response.UserActivityLogResponse;
import com.example.demo.user.entity.UserActivityLog;
import org.springframework.stereotype.Component;

@Component
public class UserActivityLogMapper {

    public UserActivityLogResponse toResponse(UserActivityLog log) {
        if (log == null) return null;
        return UserActivityLogResponse.builder()
                .id(log.getId())
                .action(log.getAction())
                .details(log.getDetails())
                .createdAt(log.getCreatedAt())
                .userEmail(log.getUser() != null ? log.getUser().getEmail() : null)
                .build();
    }
}