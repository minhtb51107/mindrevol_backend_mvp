package com.example.demo.user.controller;

import com.example.demo.user.dto.request.CreatePermissionRequest;
import com.example.demo.user.dto.request.UpdatePermissionRequest;
import com.example.demo.user.dto.response.PermissionResponse;
import com.example.demo.user.service.PermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/permissions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')") // Yêu cầu quyền ADMIN cho tất cả các API trong controller này
public class PermissionController {

    private final PermissionService permissionService;

    @PostMapping
    public ResponseEntity<PermissionResponse> createPermission(@Valid @RequestBody CreatePermissionRequest request) {
        PermissionResponse newPermission = permissionService.createPermission(request);
        return new ResponseEntity<>(newPermission, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<PermissionResponse>> getAllPermissions() {
        List<PermissionResponse> permissions = permissionService.getAllPermissions();
        return ResponseEntity.ok(permissions);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PermissionResponse> updatePermission(
            @PathVariable("id") Integer permissionId,
            @Valid @RequestBody UpdatePermissionRequest request) {
        PermissionResponse updatedPermission = permissionService.updatePermission(permissionId, request);
        return ResponseEntity.ok(updatedPermission);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePermission(@PathVariable("id") Integer permissionId) {
        permissionService.deletePermission(permissionId);
        return ResponseEntity.noContent().build(); // Trả về HTTP 204 No Content
    }
}