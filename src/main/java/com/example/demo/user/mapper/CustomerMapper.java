package com.example.demo.user.mapper;

import org.springframework.stereotype.Component;

import com.example.demo.auth.dto.request.RegisterRequest;
import com.example.demo.user.dto.response.CustomerResponse;
import com.example.demo.user.entity.Customer;

@Component // Đánh dấu đây là một Spring Bean
public class CustomerMapper {

    // Chuyển từ RegisterRequest DTO sang Customer Entity
    public Customer toCustomerEntity(RegisterRequest request) {
        if (request == null) {
            return null;
        }
        return Customer.builder()
                .fullname(request.getFullname())
                .phoneNumber(request.getPhoneNumber())
                .build();
    }

    // Chuyển từ Customer Entity sang CustomerResponse DTO
    public CustomerResponse toCustomerResponse(Customer customer) {
        if (customer == null) {
            return null;
        }
        return CustomerResponse.builder()
                .id(customer.getId())
                .fullname(customer.getFullname())
                .phoneNumber(customer.getPhoneNumber())
                .photo(customer.getPhoto())
                .email(customer.getUser() != null ? customer.getUser().getEmail() : null)
                .status(customer.getUser() != null ? customer.getUser().getStatus().name() : null)
                .build();
    }
}