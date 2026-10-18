package com.meesho.gst.service;

import com.meesho.gst.entity.User;
import com.meesho.gst.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OAuth2User oAuth2User;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository);
    }

    @Test
    @DisplayName("Should create and save new user when logging in for the first time")
    void testProcessOAuthPostLogin_NewUser() {
        when(oAuth2User.getAttribute("email")).thenReturn("john.doe@example.com");
        when(oAuth2User.getAttribute("name")).thenReturn("John Doe");
        when(oAuth2User.getAttribute("picture")).thenReturn("https://lh3.googleusercontent.com/avatar.jpg");
        when(oAuth2User.getAttribute("sub")).thenReturn("google-sub-12345");

        when(userRepository.findByEmail("john.doe@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByGoogleId("google-sub-12345")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(1L);
            return u;
        });

        User savedUser = userService.processOAuthPostLogin(oAuth2User);

        assertNotNull(savedUser);
        assertEquals(1L, savedUser.getId());
        assertEquals("john.doe@example.com", savedUser.getEmail());
        assertEquals("John Doe", savedUser.getName());
        assertEquals("https://lh3.googleusercontent.com/avatar.jpg", savedUser.getPicture());
        assertEquals("google-sub-12345", savedUser.getGoogleId());
        assertEquals("GOOGLE", savedUser.getProvider());
        assertNotNull(savedUser.getLastLoginAt());

        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("Should update existing user details and update lastLoginAt on subsequent logins")
    void testProcessOAuthPostLogin_ExistingUser() {
        User existingUser = new User();
        existingUser.setId(42L);
        existingUser.setEmail("jane.smith@example.com");
        existingUser.setName("Old Name");
        existingUser.setPicture("https://old-pic.com");
        existingUser.setGoogleId("google-sub-99999");
        existingUser.setLastLoginAt(LocalDateTime.now().minusDays(10));

        when(oAuth2User.getAttribute("email")).thenReturn("jane.smith@example.com");
        when(oAuth2User.getAttribute("name")).thenReturn("Jane Smith");
        when(oAuth2User.getAttribute("picture")).thenReturn("https://new-pic.com/avatar.png");
        when(oAuth2User.getAttribute("sub")).thenReturn("google-sub-99999");

        when(userRepository.findByEmail("jane.smith@example.com")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User updatedUser = userService.processOAuthPostLogin(oAuth2User);

        assertNotNull(updatedUser);
        assertEquals(42L, updatedUser.getId());
        assertEquals("jane.smith@example.com", updatedUser.getEmail());
        assertEquals("Jane Smith", updatedUser.getName());
        assertEquals("https://new-pic.com/avatar.png", updatedUser.getPicture());
        assertTrue(updatedUser.getLastLoginAt().isAfter(LocalDateTime.now().minusMinutes(1)));

        verify(userRepository, times(1)).save(existingUser);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when both email and googleId are null")
    void testProcessOAuthPostLogin_MissingIdentifiers() {
        assertThrows(IllegalArgumentException.class, () ->
                userService.processOAuthPostLogin(null, "No Email", null, null, "GOOGLE")
        );
        verify(userRepository, never()).save(any());
    }
}
