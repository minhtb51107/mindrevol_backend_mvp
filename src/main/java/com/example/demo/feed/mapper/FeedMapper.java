package com.example.demo.feed.mapper;

import com.example.demo.feed.dto.FeedEventDto;
import com.example.demo.feed.entity.FeedEvent;
import com.example.demo.user.entity.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor // Inject ObjectMapper
public class FeedMapper {

    private final ObjectMapper objectMapper; // Dùng để parse chuỗi JSON thành JsonNode

    public FeedEventDto toDto(FeedEvent entity) {
        if (entity == null) {
            return null;
        }

        JsonNode detailsNode = null;
        if (entity.getDetails() != null) {
            try {
                detailsNode = objectMapper.readTree(entity.getDetails());
            } catch (JsonProcessingException e) {
                log.error("Error parsing FeedEvent details JSON (ID: {}): {}", entity.getId(), e.getMessage());
                // Có thể trả về null hoặc một JsonNode rỗng tùy theo yêu cầu
                detailsNode = objectMapper.createObjectNode(); // Trả về object rỗng nếu lỗi parse
            }
        }

        return FeedEventDto.builder()
                .id(entity.getId())
                .eventType(entity.getEventType())
                .timestamp(entity.getTimestamp())
                .actorId(entity.getActor() != null ? entity.getActor().getId() : null)
                .actorFullName(entity.getActor() != null ? getUserFullName(entity.getActor()) : null) // Sử dụng helper
                .planId(entity.getPlan() != null ? entity.getPlan().getId() : null)
                .planTitle(entity.getPlan() != null ? entity.getPlan().getTitle() : null)
                .details(detailsNode) // Gán JsonNode đã parse
                .build();
    }

    // Helper lấy tên đầy đủ (copy từ các mapper khác)
    public String getUserFullName(User user) {
        if (user == null) return null;
        // Ưu tiên Employee trước
        if (user.getEmployee() != null && user.getEmployee().getFullname() != null && !user.getEmployee().getFullname().isBlank()) {
            return user.getEmployee().getFullname();
        }
        // Sau đó Customer
        if (user.getCustomer() != null && user.getCustomer().getFullname() != null && !user.getCustomer().getFullname().isBlank()) {
            return user.getCustomer().getFullname();
        }
        // Cuối cùng là email
        return user.getEmail();
    }
}