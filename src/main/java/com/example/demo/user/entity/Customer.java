// File: src/main/java/com/example/demo/user/entity/Customer.java (Bản sửa lỗi cuối cùng)

package com.example.demo.user.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false, unique = true)
    private User user;

    // SỬA LỖI Ở ĐÂY: Ánh xạ tường minh tới cột "full_name"
    @Column(name = "full_name", nullable = false, length = 100)
    private String fullname;

    // SỬA LỖI Ở ĐÂY: Ánh xạ tường minh tới cột "phone_number"
    @Column(name = "phone_number", unique = true, length = 20)
    private String phoneNumber;

    @Column(name = "photo", length = 255)
    private String photo;
}