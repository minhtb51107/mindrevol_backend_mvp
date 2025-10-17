package com.example.demo.auth.repository;

import com.example.demo.auth.entity.UserActivationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserActivationTokenRepository extends JpaRepository<UserActivationToken, Long> {

    /**
     * Tìm token bằng chuỗi token.
     * @param token Chuỗi token duy nhất.
     * @return Optional chứa UserActivationToken nếu tìm thấy.
     */
    Optional<UserActivationToken> findByToken(String token);
}