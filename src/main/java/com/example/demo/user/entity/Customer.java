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

    // Quan hệ 1-1, user_id là khóa ngoại và là duy nhất
    @OneToOne(fetch = FetchType.LAZY) // <-- XÓA BỎ cascade = CascadeType.ALL
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false, unique = true)
    private User user;

    @Column(name = "fullname", nullable = false, length = 100)
    private String fullname;

    @Column(name = "phone_number", unique = true, length = 20) // Xóa nullable = false
    private String phoneNumber;

    @Column(name = "photo", length = 255)
    private String photo;
}
