package com.meesho.gst.controller;

import com.meesho.gst.entity.User;
import com.meesho.gst.service.UserService;
import com.meesho.gst.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private UserService userService;

    @Mock
    private OAuth2User principal;

    private AuthController authController;

    @BeforeEach
    void setUp() {
        authController = new AuthController(jwtUtil, userService);
    }

    @Test
    @DisplayName("GET /api/auth/user returns authenticated=false when principal is null")
    void testGetCurrentUser_Unauthenticated() {
        ResponseEntity<Map<String, Object>> response = authController.getCurrentUser(null);
        assertNotNull(response.getBody());
        assertEquals(Boolean.FALSE, response.getBody().get("authenticated"));
    }

    @Test
    @DisplayName("GET /api/auth/user returns user profile and DB userId when principal is present")
    void testGetCurrentUser_AuthenticatedWithDbUser() {
        when(principal.getAttribute("email")).thenReturn("alex@example.com");
        when(principal.getAttribute("name")).thenReturn("Alex Smith");
        when(principal.getAttribute("picture")).thenReturn("https://img.com/alex.jpg");
        when(principal.getAttribute("sub")).thenReturn("sub-12345");

        User dbUser = new User();
        dbUser.setId(5L);
        dbUser.setEmail("alex@example.com");
        dbUser.setName("Alex Smith");
        dbUser.setPicture("https://img.com/alex.jpg");

        when(userService.findByEmail("alex@example.com")).thenReturn(Optional.of(dbUser));

        ResponseEntity<Map<String, Object>> response = authController.getCurrentUser(principal);
        assertNotNull(response.getBody());
        assertEquals(Boolean.TRUE, response.getBody().get("authenticated"));
        assertEquals("alex@example.com", response.getBody().get("email"));
        assertEquals("Alex Smith", response.getBody().get("name"));
        assertEquals(5L, response.getBody().get("userId"));
    }

    @Test
    @DisplayName("GET /api/auth/token returns 401 when principal is null")
    void testGetJwtToken_Unauthenticated() {
        ResponseEntity<Map<String, String>> response = authController.getJwtToken(null);
        assertEquals(401, response.getStatusCode().value());
    }

    @Test
    @DisplayName("GET /api/auth/token generates and returns JWT token when authenticated")
    void testGetJwtToken_Authenticated() {
        when(principal.getAttribute("email")).thenReturn("alex@example.com");
        when(principal.getAttribute("name")).thenReturn("Alex");
        when(principal.getAttribute("picture")).thenReturn("https://img.com/alex.jpg");
        when(principal.getAttribute("sub")).thenReturn("sub-12345");
        when(jwtUtil.generateToken(any())).thenReturn("valid.jwt.token");

        ResponseEntity<Map<String, String>> response = authController.getJwtToken(principal);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("valid.jwt.token", response.getBody().get("token"));
    }
}
