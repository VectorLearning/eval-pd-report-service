package com.evplus.report.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JPA Entity for users.
 * Maps to the existing 'users' table in the database.
 * Read-only entity used to fetch user details for notifications.
 */
@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    /**
     * Auto-generated primary key.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id", nullable = false)
    private Integer userId;

    /**
     * District ID (foreign key).
     */
    @Column(name = "district_id")
    private Integer districtId;

    /**
     * Username for authentication (mapped to 'login' column).
     */
    @Column(name = "login", length = 128)
    private String username;

    /**
     * User's email address.
     */
    @Column(name = "email", length = 128)
    private String email;

    /**
     * User's first name.
     */
    @Column(name = "first_name", length = 128)
    private String firstName;

    /**
     * User's last name.
     */
    @Column(name = "last_name", length = 128)
    private String lastName;
}
