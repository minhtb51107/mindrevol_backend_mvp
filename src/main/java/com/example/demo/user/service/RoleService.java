package com.example.demo.user.service;


import java.util.List;

import com.example.demo.user.dto.request.AssignPermissionToRoleRequest;
import com.example.demo.user.dto.request.CreateRoleRequest;
import com.example.demo.user.dto.response.RoleWithPermissionsResponse;

public interface RoleService {

    /**
     * Tạo một vai trò mới trong hệ thống.
     * @param request DTO chứa tên và mô tả của vai trò mới.
     * @return DTO chứa thông tin vai trò vừa tạo.
     */
    RoleWithPermissionsResponse createRole(CreateRoleRequest request);

    /**
     * Lấy danh sách tất cả vai trò cùng với các quyền đi kèm.
     * @return List các DTO của vai trò.
     */
    List<RoleWithPermissionsResponse> getAllRoles();

    /**
     * Gán một tập hợp các quyền cho một vai trò.
     * @param roleId ID của vai trò cần gán quyền.
     * @param request DTO chứa danh sách tên các quyền.
     * @return DTO của vai trò sau khi đã được cập nhật quyền.
     */
    RoleWithPermissionsResponse assignPermissionsToRole(Integer roleId, AssignPermissionToRoleRequest request);
    
    /**
     * Xóa một vai trò khỏi hệ thống.
     * @param roleId ID của vai trò cần xóa.
     */
    void deleteRole(Integer roleId);
}
