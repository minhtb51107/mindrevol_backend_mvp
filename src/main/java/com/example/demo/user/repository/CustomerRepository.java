package com.example.demo.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.user.entity.Customer;

import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Integer> {

    /**
     * Tìm kiếm Customer bằng số điện thoại.
     * @param phoneNumber Số điện thoại cần tìm.
     * @return Optional chứa Customer nếu tìm thấy.
     */
    Optional<Customer> findByPhoneNumber(String phoneNumber);

    /**
     * Tìm kiếm Customer bằng user_id.
     * @param userId ID của User liên kết.
     * @return Optional chứa Customer nếu tìm thấy.
     */
    Optional<Customer> findByUser_Id(Integer userId);

    /**
     * Kiểm tra sự tồn tại của Customer qua số điện thoại.
     * @param phoneNumber Số điện thoại cần kiểm tra.
     * @return true nếu số điện thoại đã tồn tại.
     */
    boolean existsByPhoneNumber(String phoneNumber);
}