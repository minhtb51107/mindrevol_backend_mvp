package com.example.demo.user.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class PermissionResponse {
    private Integer id;
    private String name;
    private String description;
}