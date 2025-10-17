package com.example.demo.user.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "employees")
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "employee_code", nullable = false, unique = true, length = 20)
    private String employeeCode;

    @OneToOne(fetch = FetchType.LAZY) // <-- XÓA BỎ cascade = CascadeType.ALL
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false, unique = true)
    private User user;

    @Column(name = "fullname", nullable = false, length = 100)
    private String fullname;

    @Column(name = "position", length = 100)
    private String position;

    @Column(name = "department", length = 100)
    private String department;

    @Column(name = "hired_date", nullable = false)
    private LocalDate hiredDate;

    @Builder.Default // <-- THÊM ANNOTATION NÀY
    @Column(name = "is_active")
    private boolean active = true;

    // --- Relationships ---

    @ManyToMany // XÓA BỎ: fetch = FetchType.EAGER
    @JoinTable(
        name = "employee_roles",
        joinColumns = @JoinColumn(name = "employee_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles;
}