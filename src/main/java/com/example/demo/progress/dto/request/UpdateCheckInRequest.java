package com.example.demo.progress.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

/**
 * DTO cho request cập nhật CheckInEvent.
 * Tương tự CheckInRequest nhưng không bao gồm file (V1 không hỗ trợ sửa file).
 */
@Data
public class UpdateCheckInRequest {

    @Size(max = 2000, message = "Ghi chú quá dài (tối đa 2000 ký tự)")
    private String notes;

    private List<Long> completedTaskIds;
    
    @Size(max = 10, message = "Chỉ được tối đa 10 liên kết")
    private List<String> links;
}