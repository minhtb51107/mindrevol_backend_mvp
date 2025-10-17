package com.example.demo.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.user.entity.User;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {

    /**
     * Tìm kiếm một User dựa trên địa chỉ email.
     * Dùng Optional để xử lý trường hợp email không tồn tại một cách an toàn.
     * @param email Địa chỉ email cần tìm.
     * @return Optional chứa User nếu tìm thấy, ngược lại trả về Optional rỗng.
     */
    Optional<User> findByEmail(String email);

    /**
     * Kiểm tra xem một email đã tồn tại trong CSDL hay chưa.
     * Hiệu quả hơn việc lấy cả object User về chỉ để kiểm tra.
     * @param email Địa chỉ email cần kiểm tra.
     * @return true nếu email đã tồn tại, false nếu chưa.
     */
    boolean existsByEmail(String email);
}