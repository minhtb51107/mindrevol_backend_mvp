package com.example.demo.user.dto.request;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.util.Set;

@Getter
@Setter
public class CreateEmployeeRequest {

    @NotBlank(message = "Họ tên không được để trống")
    private String fullname;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không đúng định dạng")
    private String email;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(min = 8, message = "Mật khẩu phải có ít nhất 8 ký tự")
    private String password;

    @NotBlank(message = "Mã nhân viên không được để trống")
    private String employeeCode;
    
    private String position;
    private String department;
    private LocalDate hiredDate;

    @NotEmpty(message = "Phải chỉ định ít nhất một vai trò")
    private Set<String> roleNames; // Dùng tên vai trò (ví dụ: "SALE_MANAGER") để gán
}