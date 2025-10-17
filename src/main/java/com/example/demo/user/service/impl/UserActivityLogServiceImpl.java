package com.example.demo.user.service.impl;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async; // Dùng để ghi log bất đồng bộ
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.user.dto.response.UserActivityLogResponse;
import com.example.demo.user.entity.User;
import com.example.demo.user.entity.UserActivityLog;
import com.example.demo.user.mapper.UserActivityLogMapper;
import com.example.demo.user.repository.UserActivityLogRepository;
import com.example.demo.user.service.UserActivityLogService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserActivityLogServiceImpl implements UserActivityLogService {

    private final UserActivityLogRepository logRepository;
    private final UserActivityLogMapper logMapper;
    private final ObjectMapper objectMapper; // Spring Boot tự động cung cấp bean này

    @Override
    @Async // Chạy trên một thread khác để không làm chậm luồng chính
    @Transactional
    public void logActivity(String action, Object details, User user) {
        try {
            UserActivityLog logEntry = new UserActivityLog();
            logEntry.setAction(action);
            logEntry.setUser(user);
            
            // Chuyển đổi đối tượng details thành chuỗi JSON
            logEntry.setDetails(objectMapper.writeValueAsString(details));
            
            logRepository.save(logEntry);
        } catch (JsonProcessingException e) {
            log.error("Lỗi khi chuyển đổi chi tiết log thành JSON: {}", e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserActivityLogResponse> getActivityLogs(Pageable pageable) {
        return logRepository.findAll(pageable)
                .map(logMapper::toResponse);
    }
    
    @Override
    @Transactional(readOnly = true)
    public Page<UserActivityLogResponse> searchLogs(Integer userId, String action, OffsetDateTime startDate, OffsetDateTime endDate, Pageable pageable) {
        
        // Sử dụng Specification để xây dựng câu lệnh WHERE động
        Specification<UserActivityLog> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (userId != null) {
                predicates.add(criteriaBuilder.equal(root.get("user").get("id"), userId));
            }
            if (action != null && !action.isBlank()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("action")), "%" + action.toLowerCase() + "%"));
            }
            if (startDate != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            }
            if (endDate != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        return logRepository.findAll(spec, pageable)
                .map(logMapper::toResponse);
    }
    
}
