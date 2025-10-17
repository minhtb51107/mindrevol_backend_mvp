package com.example.demo.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.user.entity.Role;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Integer> {

    /**
     * Tìm kiếm một Role bằng tên (duy nhất).
     * @param name Tên của Role (ví dụ: "ADMIN", "USER").
     * @return Optional chứa Role nếu tìm thấy.
     */
    Optional<Role> findByName(String name);
    
    /**
     * Đếm số lượng vai trò có chứa một quyền cụ thể.
     * @param permissionId ID của quyền cần kiểm tra.
     * @return Số lượng vai trò đang sử dụng quyền này.
     */
    long countByPermissions_Id(Integer permissionId);
}