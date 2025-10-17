package com.example.demo.user.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.user.dto.request.AssignPermissionToRoleRequest;
import com.example.demo.user.dto.request.CreateRoleRequest;
import com.example.demo.user.dto.response.RoleWithPermissionsResponse;
import com.example.demo.user.service.RoleService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')") // Mọi hành động với Role đều cần quyền Admin
public class RoleController {

    private final RoleService roleService;

    @PostMapping
    public ResponseEntity<RoleWithPermissionsResponse> createRole(@Valid @RequestBody CreateRoleRequest request) {
        RoleWithPermissionsResponse newRole = roleService.createRole(request);
        return new ResponseEntity<>(newRole, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<RoleWithPermissionsResponse>> getAllRoles() {
        List<RoleWithPermissionsResponse> roles = roleService.getAllRoles();
        return ResponseEntity.ok(roles);
    }

    @PostMapping("/{id}/permissions")
    public ResponseEntity<RoleWithPermissionsResponse> assignPermissions(
            @PathVariable("id") Integer roleId,
            @Valid @RequestBody AssignPermissionToRoleRequest request) {
        RoleWithPermissionsResponse updatedRole = roleService.assignPermissionsToRole(roleId, request);
        return ResponseEntity.ok(updatedRole);
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRole(@PathVariable("id") Integer roleId) {
        roleService.deleteRole(roleId);
        return ResponseEntity.noContent().build();
    }
}