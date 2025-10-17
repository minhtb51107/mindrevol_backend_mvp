package com.example.demo.user.mapper;

import com.example.demo.user.dto.response.RoleWithPermissionsResponse;
import com.example.demo.user.entity.Permission;
import com.example.demo.user.entity.Role;
import org.springframework.stereotype.Component;
import java.util.Collections;
import java.util.stream.Collectors;

@Component
public class RoleMapper {

    public RoleWithPermissionsResponse toRoleWithPermissionsResponse(Role role) {
        if (role == null) return null;
        return RoleWithPermissionsResponse.builder()
                .id(role.getId())
                .name(role.getName())
                .description(role.getDescription())
                .permissions(role.getPermissions() != null ?
                        role.getPermissions().stream().map(this::toPermissionResponse).collect(Collectors.toSet()) :
                        Collections.emptySet())
                .build();
    }

    public RoleWithPermissionsResponse.PermissionResponse toPermissionResponse(Permission permission) {
        if (permission == null) return null;
        return RoleWithPermissionsResponse.PermissionResponse.builder()
                .name(permission.getName())
                .description(permission.getDescription())
                .build();
    }
}