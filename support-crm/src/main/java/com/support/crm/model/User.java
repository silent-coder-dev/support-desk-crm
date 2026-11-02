package com.support.crm.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    @ToString.Exclude // Password hash must never reach the logs
    private String password;

    @Column(nullable = false)
    private String fullname;

    @Column(unique = true)
    private String email;

    @Column(name = "mobile_number", unique = true)
    private String mobileNumber;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(nullable = false)
    private String role;

    // A disabled account can't log in, but stays in the database so old tickets keep their agent's name
    @Column(nullable = false)
    @Builder.Default
    private boolean disabled = false;
}