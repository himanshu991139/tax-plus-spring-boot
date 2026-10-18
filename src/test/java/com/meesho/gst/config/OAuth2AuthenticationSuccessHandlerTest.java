package com.meesho.gst.config;

import com.meesho.gst.entity.User;
import com.meesho.gst.service.UserService;
import com.meesho.gst.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuth2AuthenticationSuccessHandlerTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private UserService userService;

    @Mock
    private Authentication authentication;

    @Mock
    private OAuth2User oAuth2User;

    private OAuth2AuthenticationSuccessHandler successHandler;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        successHandler = new OAuth2AuthenticationSuccessHandler(jwtUtil, userService);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("Should save user to database and redirect to frontend with JWT token")
    void testOnAuthenticationSuccess() throws Exception {
        when(authentication.getPrincipal()).thenReturn(oAuth2User);

        when(oAuth2User.getAttribute("email")).thenReturn("user@taxpulse.com");
        when(oAuth2User.getAttribute("name")).thenReturn("Tax User");
        when(oAuth2User.getAttribute("picture")).thenReturn("https://avatar.com/pic.png");
        when(oAuth2User.getAttribute("sub")).thenReturn("google-sub-777");

        User savedUser = new User();
        savedUser.setId(10L);
        savedUser.setEmail("user@taxpulse.com");
        when(userService.processOAuthPostLogin(oAuth2User)).thenReturn(savedUser);

        when(jwtUtil.generateToken(any())).thenReturn("mock.jwt.token");

        successHandler.onAuthenticationSuccess(request, response, authentication);

        // Verify that user details are saved in DB
        verify(userService, times(1)).processOAuthPostLogin(oAuth2User);

        // Verify JWT generated with claims
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> claimsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(jwtUtil).generateToken(claimsCaptor.capture());
        Map<String, Object> capturedClaims = claimsCaptor.getValue();
        assertEquals("user@taxpulse.com", capturedClaims.get("email"));
        assertEquals("Tax User", capturedClaims.get("name"));
        assertEquals("https://avatar.com/pic.png", capturedClaims.get("picture"));
        assertEquals("google-sub-777", capturedClaims.get("sub"));
        assertEquals(10L, capturedClaims.get("userId"));

        // Verify redirection
        String redirectUrl = response.getRedirectedUrl();
        assertNotNull(redirectUrl);
        assertTrue(redirectUrl.startsWith("http://localhost:4200/app/profiles?token=mock.jwt.token"));
    }
}
