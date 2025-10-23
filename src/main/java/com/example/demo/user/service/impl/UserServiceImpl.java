package com.example.demo.user.service.impl;

import com.example.demo.shared.exception.ResourceNotFoundException;
import com.example.demo.user.dto.request.UpdateProfileRequest;
import com.example.demo.user.dto.response.ProfileResponse;
import com.example.demo.user.entity.Customer;
import com.example.demo.user.entity.Employee;
import com.example.demo.user.entity.User;
import com.example.demo.user.repository.CustomerRepository; // Cần import
import com.example.demo.user.repository.EmployeeRepository; // Cần import
import com.example.demo.user.repository.UserRepository;
import com.example.demo.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional // Đặt Transactional ở cấp class
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository; // Inject CustomerRepository
    private final EmployeeRepository employeeRepository; // Inject EmployeeRepository

    @Override
    @Transactional(readOnly = true) // Tối ưu cho đọc
    public ProfileResponse getUserProfile(String userEmail) {
        User user = findUserByEmail(userEmail);
        return mapUserToProfileResponse(user);
    }

    @Override
    public ProfileResponse updateUserProfile(String userEmail, UpdateProfileRequest request) {
        User user = findUserByEmail(userEmail);

        // Cập nhật fullname và photoUrl trong Customer hoặc Employee
        if (user.getCustomer() != null) {
            Customer customer = user.getCustomer();
            customer.setFullname(request.getFullname());
            customer.setPhoto(request.getPhotoUrl()); // Cập nhật trường photo
            // customerRepository.save(customer); // Không cần save riêng nếu User được save
        } else if (user.getEmployee() != null) {
            Employee employee = user.getEmployee();
            employee.setFullname(request.getFullname());
            // Employee hiện chưa có trường photo, có thể bỏ qua hoặc thêm sau
            // employeeRepository.save(employee); // Không cần save riêng nếu User được save
        } else {
            // Xử lý trường hợp User không phải Customer hay Employee (nếu có thể xảy ra)
             throw new IllegalStateException("User không có thông tin Customer hoặc Employee.");
        }

        // Lưu User (do có thể thay đổi gián tiếp qua Customer/Employee,
        // hoặc để đảm bảo transaction hoạt động đúng nếu không dùng Cascade)
        // Thực tế, việc save User có thể không cần nếu không có thay đổi trực tiếp
        // trên User và cascade đang hoạt động đúng. Nhưng để an toàn, có thể save lại.
        // User updatedUser = userRepository.save(user); // Tạm thời comment nếu không cần

        // Trả về thông tin đã cập nhật (map lại từ user entity)
        return mapUserToProfileResponse(user); // Map lại từ user entity sau khi đã thay đổi
    }

    // Helper method để tìm User
    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với email: " + email));
    }

    // Helper method để map User sang ProfileResponse
    private ProfileResponse mapUserToProfileResponse(User user) {
        ProfileResponse.ProfileResponseBuilder builder = ProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .status(user.getStatus().name());

        if (user.getCustomer() != null) {
            builder.fullname(user.getCustomer().getFullname())
                   .photoUrl(user.getCustomer().getPhoto()) // Lấy photo từ Customer
                   .userType("CUSTOMER");
        } else if (user.getEmployee() != null) {
            builder.fullname(user.getEmployee().getFullname())
                   // Employee hiện chưa có photoUrl
                   .userType("EMPLOYEE");
        } else {
            builder.fullname(user.getEmail()) // Fallback nếu không có Customer/Employee
                   .userType("UNKNOWN");
        }

        return builder.build();
    }
}