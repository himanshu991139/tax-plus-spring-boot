package com.meesho.gst.config;

import com.meesho.gst.entity.User;
import com.meesho.gst.service.UserService;
import com.meesho.gst.util.JwtUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2AuthenticationSuccessHandler.class);

    private final JwtUtil jwtUtil;
    private final UserService userService;

    public OAuth2AuthenticationSuccessHandler(JwtUtil jwtUtil, UserService userService) {
        this.jwtUtil = jwtUtil;
        this.userService = userService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        if (response.isCommitted()) {
            return;
        }

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        // Save or update user details in the MySQL database
        User savedUser = null;
        try {
            savedUser = userService.processOAuthPostLogin(oAuth2User);
            log.info("Saved/Updated Google OAuth user in DB with id={}, email={}", savedUser.getId(), savedUser.getEmail());
        } catch (Exception ex) {
            log.error("Failed to persist user details in database upon Google login", ex);
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put("name", oAuth2User.getAttribute("name"));
        claims.put("email", oAuth2User.getAttribute("email"));
        claims.put("picture", oAuth2User.getAttribute("picture"));
        claims.put("sub", oAuth2User.getAttribute("sub"));
        if (savedUser != null && savedUser.getId() != null) {
            claims.put("userId", savedUser.getId());
        }

        String token = jwtUtil.generateToken(claims);

        String targetUrl = UriComponentsBuilder.fromUriString("http://localhost:4200/app/profiles")
                .queryParam("token", token)
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
