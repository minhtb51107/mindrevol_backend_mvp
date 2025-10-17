package com.example.demo.user.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.util.Set;

@Getter
@Setter
@Builder
public class RoleWithPermissionsResponse {
    private Integer id;
    private String name;
    private String description;
    private Set<PermissionResponse> permissions;

    // Lớp DTO lồng nhau để chỉ hiển thị thông tin cần thiết của Permission
    @Getter
    @Setter
    @Builder
    public static class PermissionResponse {
        private String name;
        private String description;
    }
}