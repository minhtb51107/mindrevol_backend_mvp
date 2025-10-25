// src/test/java/com/example/demo/user/service/impl/UserServiceImplTest.java
package com.example.demo.user.service.impl;

import com.example.demo.shared.exception.ResourceNotFoundException;
import com.example.demo.user.dto.request.UpdateProfileRequest;
import com.example.demo.user.dto.response.ProfileResponse;
import com.example.demo.user.entity.Customer;
import com.example.demo.user.entity.Employee;
import com.example.demo.user.entity.User;
import com.example.demo.user.entity.UserStatus;
import com.example.demo.user.repository.CustomerRepository;
import com.example.demo.user.repository.EmployeeRepository;
import com.example.demo.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private CustomerRepository customerRepository; // Mocked, used in update
    @Mock private EmployeeRepository employeeRepository; // Mocked, used in update

    @InjectMocks
    private UserServiceImpl userService;

    private User customerUser;
    private User employeeUser;
    private Customer customer;
    private Employee employee;

    @BeforeEach
    void setUp() {
        customer = Customer.builder().id(1).fullname("Customer Name").photo("customer.jpg").build();
        customerUser = User.builder()
                .id(1)
                .email("customer@example.com")
                .status(UserStatus.ACTIVE)
                .customer(customer)
                .build();
        customer.setUser(customerUser); // Link back

        employee = Employee.builder().id(1).fullname("Employee Name").build();
        employeeUser = User.builder()
                .id(2)
                .email("employee@example.com")
                .status(UserStatus.ACTIVE)
                .employee(employee)
                .build();
        employee.setUser(employeeUser); // Link back
    }

    @Test
    void getUserProfile_CustomerFound() {
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(customerUser));

        ProfileResponse response = userService.getUserProfile("customer@example.com");

        assertNotNull(response);
        assertEquals(customerUser.getId(), response.getId());
        assertEquals(customerUser.getEmail(), response.getEmail());
        assertEquals("Customer Name", response.getFullname());
        assertEquals("customer.jpg", response.getPhotoUrl());
        assertEquals("CUSTOMER", response.getUserType());
        assertEquals("ACTIVE", response.getStatus());
    }

    @Test
    void getUserProfile_EmployeeFound() {
        when(userRepository.findByEmail("employee@example.com")).thenReturn(Optional.of(employeeUser));

        ProfileResponse response = userService.getUserProfile("employee@example.com");

        assertNotNull(response);
        assertEquals(employeeUser.getId(), response.getId());
        assertEquals(employeeUser.getEmail(), response.getEmail());
        assertEquals("Employee Name", response.getFullname());
        assertNull(response.getPhotoUrl()); // Employee doesn't have photoUrl yet
        assertEquals("EMPLOYEE", response.getUserType());
        assertEquals("ACTIVE", response.getStatus());
    }

     @Test
    void getUserProfile_UserNotFound() {
        when(userRepository.findByEmail("notfound@example.com")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.getUserProfile("notfound@example.com"));
    }

    @Test
    void updateUserProfile_CustomerSuccess() {
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(customerUser));
        // No need to mock customerRepository.save() if User save cascades or if we don't explicitly save customer

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullname("Updated Customer");
        request.setPhotoUrl("new_photo.png");

        ProfileResponse response = userService.updateUserProfile("customer@example.com", request);

        assertNotNull(response);
        assertEquals("Updated Customer", response.getFullname());
        assertEquals("new_photo.png", response.getPhotoUrl());
        // Verify changes were made to the entity object (before potential save)
        assertEquals("Updated Customer", customerUser.getCustomer().getFullname());
        assertEquals("new_photo.png", customerUser.getCustomer().getPhoto());
        // verify(customerRepository).save(customerUser.getCustomer()); // Verify if you explicitly save customer
        // verify(userRepository).save(customerUser); // Verify if you explicitly save user
    }

     @Test
    void updateUserProfile_EmployeeSuccess() {
        when(userRepository.findByEmail("employee@example.com")).thenReturn(Optional.of(employeeUser));

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullname("Updated Employee");
        // request.setPhotoUrl("ignored_photo.png"); // PhotoUrl is ignored for Employee for now

        ProfileResponse response = userService.updateUserProfile("employee@example.com", request);

        assertNotNull(response);
        assertEquals("Updated Employee", response.getFullname());
        assertNull(response.getPhotoUrl()); // Still null
        assertEquals("Updated Employee", employeeUser.getEmployee().getFullname());
        // verify(employeeRepository).save(employeeUser.getEmployee()); // Verify if you explicitly save employee
    }

      @Test
    void updateUserProfile_UserNotFound() {
        when(userRepository.findByEmail("notfound@example.com")).thenReturn(Optional.empty());

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullname("Update Attempt");

        assertThrows(ResourceNotFoundException.class, () -> userService.updateUserProfile("notfound@example.com", request));
        verify(customerRepository, never()).save(any());
        verify(employeeRepository, never()).save(any());
        verify(userRepository, never()).save(any());
    }
}