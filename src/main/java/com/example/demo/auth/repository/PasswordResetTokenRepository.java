package com.example.demo.auth.repository;

import com.example.demo.auth.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    /**
     * Tìm token bằng chuỗi token.
     * @param token Chuỗi token duy nhất.
     * @return Optional chứa PasswordResetToken nếu tìm thấy.
     */
    Optional<PasswordResetToken> findByToken(String token);

}