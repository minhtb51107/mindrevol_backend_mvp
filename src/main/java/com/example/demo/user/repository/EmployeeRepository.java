package com.example.demo.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.user.entity.Employee;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Integer> {

    /**
     * Tìm kiếm Employee bằng mã nhân viên duy nhất.
     * @param employeeCode Mã nhân viên.
     * @return Optional chứa Employee nếu tìm thấy.
     */
    Optional<Employee> findByEmployeeCode(String employeeCode);
    
    /**
     * Tìm kiếm Employee bằng user_id.
     * @param userId ID của User liên kết.
     * @return Optional chứa Employee nếu tìm thấy.
     */
    Optional<Employee> findByUser_Id(Integer userId);

    /**
     * Lấy danh sách nhân viên theo phòng ban.
     * @param department Tên phòng ban.
     * @return List các Employee thuộc phòng ban đó.
     */
    List<Employee> findByDepartment(String department);

    /**
     * Lấy danh sách nhân viên theo trạng thái hoạt động.
     * @param isActive true để tìm nhân viên đang hoạt động, false ngược lại.
     * @return List các Employee theo trạng thái.
     */
    List<Employee> findByActive(boolean isActive);
    
    /**
     * Đếm số lượng nhân viên có một vai trò cụ thể.
     * @param roleId ID của vai trò cần kiểm tra.
     * @return Số lượng nhân viên đang có vai trò này.
     */
    long countByRoles_Id(Integer roleId);
}