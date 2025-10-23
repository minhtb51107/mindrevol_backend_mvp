package com.example.demo.plan.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ReorderTasksRequest {

    // Danh sách các ID của Task theo thứ tự mới mong muốn
    @NotEmpty(message = "Danh sách ID công việc không được rỗng")
    private List<Long> orderedTaskIds;
}