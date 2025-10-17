package com.example.demo.user.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.example.demo.user.dto.request.UpdateCustomerRequest;
import com.example.demo.user.dto.response.CustomerResponse;

public interface CustomerService {
    
    /**
     * Lấy thông tin chi tiết một khách hàng bằng ID.
     * @param id ID của khách hàng.
     * @return DTO chứa thông tin khách hàng.
     */
    CustomerResponse getCustomerById(Integer id);

    /**
     * Lấy danh sách khách hàng có phân trang.
     * @param pageable Đối tượng chứa thông tin phân trang.
     * @return Một trang (Page) chứa danh sách khách hàng.
     */
    Page<CustomerResponse> getAllCustomers(Pageable pageable);
    
    CustomerResponse updateCustomerInfo(Integer id, UpdateCustomerRequest request);
    
    void deleteCustomer(Integer id);
}
