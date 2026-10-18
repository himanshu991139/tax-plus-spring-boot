package com.meesho.gst.service;

import com.meesho.gst.entity.User;
import com.meesho.gst.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Saves or updates user details extracted from Google OAuth2 login in the database.
     *
     * @param oAuth2User the authenticated OAuth2 user principal
     * @return the persisted User entity
     */
    @Transactional
    public User processOAuthPostLogin(OAuth2User oAuth2User) {
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        String picture = oAuth2User.getAttribute("picture");
        String sub = oAuth2User.getAttribute("sub");

        return processOAuthPostLogin(email, name, picture, sub, "GOOGLE");
    }

    /**
     * Saves or updates user details in the database.
     */
    @Transactional
    public User processOAuthPostLogin(String email, String name, String picture, String googleId, String provider) {
        if (email == null && googleId == null) {
            throw new IllegalArgumentException("User email or Google ID must be provided.");
        }

        Optional<User> existingUserOpt = Optional.empty();
        if (email != null && !email.isBlank()) {
            existingUserOpt = userRepository.findByEmail(email.trim().toLowerCase());
        }
        if (existingUserOpt.isEmpty() && googleId != null && !googleId.isBlank()) {
            existingUserOpt = userRepository.findByGoogleId(googleId.trim());
        }

        User user;
        if (existingUserOpt.isPresent()) {
            user = existingUserOpt.get();
            log.info("Updating existing user on Google login: email={}, googleId={}", user.getEmail(), user.getGoogleId());
            if (name != null && !name.isBlank()) {
                user.setName(name);
            }
            if (picture != null && !picture.isBlank()) {
                user.setPicture(picture);
            }
            if (googleId != null && !googleId.isBlank()) {
                user.setGoogleId(googleId);
            }
            if (provider != null && !provider.isBlank()) {
                user.setProvider(provider);
            }
            user.setLastLoginAt(LocalDateTime.now());
        } else {
            String sanitizedEmail = email != null ? email.trim().toLowerCase() : null;
            log.info("Creating new user on Google login: email={}, name={}", sanitizedEmail, name);
            user = new User();
            user.setEmail(sanitizedEmail);
            user.setName(name);
            user.setPicture(picture);
            user.setGoogleId(googleId);
            user.setProvider(provider != null ? provider : "GOOGLE");
            user.setLastLoginAt(LocalDateTime.now());
        }

        return userRepository.save(user);
    }

    public Optional<User> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByEmail(email.trim().toLowerCase());
    }

    public Optional<User> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return userRepository.findById(id);
    }

    public Optional<User> findByGoogleId(String googleId) {
        if (googleId == null || googleId.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByGoogleId(googleId.trim());
    }
}
