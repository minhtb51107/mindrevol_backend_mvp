// src/test/java/com/example/demo/user/repository/EmployeeRepositoryTest.java
package com.example.demo.user.repository;

import com.example.demo.user.entity.Employee;
import com.example.demo.user.entity.Role;
import com.example.demo.user.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest // Configures H2, focuses on JPA components
class EmployeeRepositoryTest {

    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private RoleRepository roleRepository; // Need to save roles first
    @Autowired
    private UserRepository userRepository; // Need to save users first
    @Autowired
    private EntityManager entityManager; // To clear persistence context if needed

    private Role roleAdmin, roleUser;
    private User user1, user2;
    private Employee emp1, emp2;

    @BeforeEach
    void setUp() {
        // Create and save dependent entities first
        roleAdmin = roleRepository.save(Role.builder().name("ADMIN").build());
        roleUser = roleRepository.save(Role.builder().name("USER").build());

        user1 = userRepository.save(User.builder().email("emp1@example.com").password("pass").build());
        user2 = userRepository.save(User.builder().email("emp2@example.com").password("pass").build());

        emp1 = Employee.builder()
                .employeeCode("E001")
                .fullname("Emp One")
                .hiredDate(LocalDate.now())
                .user(user1)
                .roles(Set.of(roleAdmin, roleUser))
                .build();
        emp2 = Employee.builder()
                .employeeCode("E002")
                .fullname("Emp Two")
                .hiredDate(LocalDate.now())
                .user(user2)
                .roles(Set.of(roleUser))
                .build();

        employeeRepository.saveAll(Set.of(emp1, emp2));
        entityManager.flush(); // Ensure data is written to DB before query
        // entityManager.clear(); // Optional: clear cache to ensure query hits DB
    }

    @Test
    void findByEmployeeCode_Exists() {
        Optional<Employee> found = employeeRepository.findByEmployeeCode("E001");
        assertTrue(found.isPresent());
        assertEquals("Emp One", found.get().getFullname());
    }

    @Test
    void findByEmployeeCode_NotExists() {
        Optional<Employee> found = employeeRepository.findByEmployeeCode("E999");
        assertFalse(found.isPresent());
    }

    @Test
    void countByRoles_Id() {
        long adminCount = employeeRepository.countByRoles_Id(roleAdmin.getId());
        long userCount = employeeRepository.countByRoles_Id(roleUser.getId());

        assertEquals(1, adminCount); // Only emp1 has ADMIN role
        assertEquals(2, userCount);  // Both emp1 and emp2 have USER role
    }

    @Test
    void countByRoles_Id_NoMatch() {
         Role roleOther = roleRepository.save(Role.builder().name("OTHER").build());
         entityManager.flush();
         long otherCount = employeeRepository.countByRoles_Id(roleOther.getId());
         assertEquals(0, otherCount);
    }
}