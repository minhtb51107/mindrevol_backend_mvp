package com.example.demo.plan.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TransferOwnershipRequest {

    @NotNull(message = "ID người dùng chủ sở hữu mới không được để trống")
    private Integer newOwnerUserId;
}