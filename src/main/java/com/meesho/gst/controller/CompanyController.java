package com.meesho.gst.controller;

import com.meesho.gst.dto.CompanyDto;
import com.meesho.gst.entity.Company;
import com.meesho.gst.service.CompanyService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/companies")
@CrossOrigin(origins = "*")
public class CompanyController {

    private final CompanyService companyService;

    @Autowired
    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @GetMapping
    public ResponseEntity<List<Company>> getCurrentUserCompanies(Authentication authentication) {
        String email = requireEmail(authentication);
        List<Company> companies = companyService.getCompaniesForUser(email);
        return ResponseEntity.ok(companies);
    }

    @PostMapping
    public ResponseEntity<Company> createCompany(
            @Valid @RequestBody CompanyDto companyDto,
            Authentication authentication) {
        Company saved = companyService.createCompany(companyDto, requireEmail(authentication));
        return new ResponseEntity<>(saved, HttpStatus.CREATED);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCompany(
            @PathVariable Long id,
            Authentication authentication) {
        companyService.deleteCompany(id, requireEmail(authentication));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/default")
    public ResponseEntity<Company> setDefaultCompany(
            @PathVariable Long id,
            Authentication authentication) {
        Company updated = companyService.setDefaultCompany(id, requireEmail(authentication));
        return ResponseEntity.ok(updated);
    }

    private String requireEmail(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof OAuth2User oAuth2User) {
            Object emailAttr = oAuth2User.getAttribute("email");
            if (emailAttr != null && !emailAttr.toString().isBlank()) {
                return emailAttr.toString().trim().toLowerCase();
            }
        }

        String name = authentication.getName();
        if (name != null && name.contains("@")) {
            return name.trim().toLowerCase();
        }

        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Logged-in user email is not available");
    }
}
