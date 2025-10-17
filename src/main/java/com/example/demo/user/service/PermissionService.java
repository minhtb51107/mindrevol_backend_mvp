package com.example.demo.user.service;

import com.example.demo.user.dto.request.CreatePermissionRequest;
import com.example.demo.user.dto.request.UpdatePermissionRequest;
import com.example.demo.user.dto.response.PermissionResponse;
import java.util.List;

public interface PermissionService {

    /**
     * Tạo một quyền mới.
     * @param request DTO chứa thông tin quyền cần tạo.
     * @return DTO của quyền vừa được tạo.
     */
    PermissionResponse createPermission(CreatePermissionRequest request);

    /**
     * Lấy danh sách tất cả các quyền.
     * @return List các DTO của quyền.
     */
    List<PermissionResponse> getAllPermissions();

    /**
     * Cập nhật thông tin mô tả của một quyền.
     * @param permissionId ID của quyền cần cập nhật.
     * @param request DTO chứa thông tin cập nhật.
     * @return DTO của quyền sau khi đã cập nhật.
     */
    PermissionResponse updatePermission(Integer permissionId, UpdatePermissionRequest request);

    /**
     * Xóa một quyền khỏi hệ thống.
     * @param permissionId ID của quyền cần xóa.
     */
    void deletePermission(Integer permissionId);
}