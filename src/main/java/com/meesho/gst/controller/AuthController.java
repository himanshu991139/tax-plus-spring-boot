package com.meesho.gst.controller;

import com.meesho.gst.entity.User;
import com.meesho.gst.service.UserService;
import com.meesho.gst.util.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtUtil jwtUtil;
    private final UserService userService;

    public AuthController(JwtUtil jwtUtil, UserService userService) {
        this.jwtUtil = jwtUtil;
        this.userService = userService;
    }

    @GetMapping("/user")
    public ResponseEntity<Map<String, Object>> getCurrentUser(@AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) {
            return ResponseEntity.ok(Collections.singletonMap("authenticated", false));
        }

        String email = principal.getAttribute("email");
        String sub = principal.getAttribute("sub");

        Map<String, Object> userData = new HashMap<>();
        userData.put("authenticated", true);
        userData.put("name", principal.getAttribute("name"));
        userData.put("email", email);
        userData.put("picture", principal.getAttribute("picture"));
        userData.put("sub", sub);

        // Fetch user from DB if available
        Optional<User> dbUser = Optional.empty();
        if (email != null) {
            dbUser = userService.findByEmail(email);
        } else if (sub != null) {
            dbUser = userService.findByGoogleId(sub);
        }

        if (dbUser.isPresent()) {
            User user = dbUser.get();
            userData.put("userId", user.getId());
            if (userData.get("name") == null) {
                userData.put("name", user.getName());
            }
            if (userData.get("picture") == null) {
                userData.put("picture", user.getPicture());
            }
        }

        return ResponseEntity.ok(userData);
    }

    @GetMapping("/token")
    public ResponseEntity<Map<String, String>> getJwtToken(@AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Collections.singletonMap("error", "Unauthenticated"));
        }
        Map<String, Object> claims = new HashMap<>();
        claims.put("name", principal.getAttribute("name"));
        claims.put("email", principal.getAttribute("email"));
        claims.put("picture", principal.getAttribute("picture"));
        claims.put("sub", principal.getAttribute("sub"));

        String email = principal.getAttribute("email");
        if (email != null) {
            userService.findByEmail(email).ifPresent(u -> claims.put("userId", u.getId()));
        }

        String token = jwtUtil.generateToken(claims);
        return ResponseEntity.ok(Collections.singletonMap("token", token));
    }
}
