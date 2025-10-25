// src/test/java/com/example/demo/shared/util/JwtUtilTest.java
package com.example.demo.shared.util;

import com.example.demo.user.entity.Employee;
import com.example.demo.user.entity.Role;
import com.example.demo.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils; // For setting private fields

import java.util.Collections;
import java.util.Date;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    // Use a fixed secret for testing, DO NOT use this in production
    private final String testSecret = "TestSecretKeyForJwtUtilWhichIsDefinitelyLongEnoughAndSecure12345";
    private final long testAccessExpiration = 3600000; // 1 hour
    private final long testRefreshExpiration = 86400000; // 1 day

    private User customerUser;
    private User employeeUser;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        // Use ReflectionTestUtils to set the private @Value fields
        ReflectionTestUtils.setField(jwtUtil, "jwtSecret", testSecret);
        ReflectionTestUtils.setField(jwtUtil, "accessTokenExpirationMs", testAccessExpiration);
        ReflectionTestUtils.setField(jwtUtil, "refreshTokenExpirationMs", testRefreshExpiration);

        // Customer User (no employee info)
        customerUser = User.builder().id(1).email("customer@example.com").build();

        // Employee User with Roles
        Role adminRole = Role.builder().id(1).name("ADMIN").permissions(Collections.emptySet()).build();
        Employee employee = Employee.builder().id(1).roles(Set.of(adminRole)).build();
        employeeUser = User.builder().id(2).email("employee@example.com").employee(employee).build();
        employee.setUser(employeeUser); // Link back
    }

    @Test
    void generateAccessToken_Customer() {
        String token = jwtUtil.generateAccessToken(customerUser);
        assertNotNull(token);

        Claims claims = Jwts.parserBuilder()
                .setSigningKey(jwtUtil.getSigningKey()) // Use the private helper via reflection or make it package-private for test
                // Or simply call validateToken first
                .build()
                .parseClaimsJws(token)
                .getBody();

        assertEquals(customerUser.getEmail(), claims.getSubject());
        assertEquals("ROLE_CUSTOMER", claims.get("roles", String.class));
        assertTrue(claims.getExpiration().after(new Date()));
        assertTrue(claims.getExpiration().getTime() - claims.getIssuedAt().getTime() <= testAccessExpiration);
    }

    @Test
    void generateAccessToken_Employee() {
        String token = jwtUtil.generateAccessToken(employeeUser);
        assertNotNull(token);

        Claims claims = Jwts.parserBuilder()
                .setSigningKey(jwtUtil.getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();

        assertEquals(employeeUser.getEmail(), claims.getSubject());
        assertEquals("ROLE_ADMIN", claims.get("roles", String.class)); // Check role mapping
        assertTrue(claims.getExpiration().after(new Date()));
    }

    @Test
    void generateRefreshToken() {
        String token = jwtUtil.generateRefreshToken(customerUser);
        assertNotNull(token);

        Claims claims = Jwts.parserBuilder()
                .setSigningKey(jwtUtil.getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();

        assertEquals(customerUser.getEmail(), claims.getSubject());
        assertNull(claims.get("roles")); // Refresh token shouldn't contain roles usually
        assertTrue(claims.getExpiration().after(new Date()));
        assertTrue(claims.getExpiration().getTime() - claims.getIssuedAt().getTime() <= testRefreshExpiration);
    }

    @Test
    void getEmailFromToken() {
        String token = jwtUtil.generateAccessToken(customerUser);
        String email = jwtUtil.getEmailFromToken(token);
        assertEquals(customerUser.getEmail(), email);
    }

    @Test
    void validateToken_Valid() {
        String token = jwtUtil.generateAccessToken(customerUser);
        assertTrue(jwtUtil.validateToken(token));
    }

    @Test
    void validateToken_Expired() throws InterruptedException {
         // Generate token with very short expiration for testing
        ReflectionTestUtils.setField(jwtUtil, "accessTokenExpirationMs", 1); // 1 ms
        String token = jwtUtil.generateAccessToken(customerUser);
        Thread.sleep(10); // Wait for token to expire
        assertFalse(jwtUtil.validateToken(token));
         // Reset expiration for other tests
         ReflectionTestUtils.setField(jwtUtil, "accessTokenExpirationMs", testAccessExpiration);
    }

     @Test
    void validateToken_InvalidSignature() {
        String token = jwtUtil.generateAccessToken(customerUser);
        // Tamper with the token (e.g., change a character in the signature part)
        String tamperedToken = token.substring(0, token.length() - 1) + "X";
        assertFalse(jwtUtil.validateToken(tamperedToken));
    }

     @Test
    void validateToken_Malformed() {
        String malformedToken = "this.is.not.a.valid.jwt";
        assertFalse(jwtUtil.validateToken(malformedToken));
    }

    @Test
    void validateToken_Empty() {
         assertFalse(jwtUtil.validateToken(""));
         assertFalse(jwtUtil.validateToken(null));
    }
}