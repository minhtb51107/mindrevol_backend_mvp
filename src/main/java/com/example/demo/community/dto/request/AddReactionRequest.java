package com.example.demo.community.dto.request;

import com.example.demo.community.entity.ReactionType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddReactionRequest {
    @NotNull(message = "Loại reaction không được để trống")
    private ReactionType reactionType;
}