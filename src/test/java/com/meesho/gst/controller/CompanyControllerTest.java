package com.meesho.gst.controller;

import com.meesho.gst.dto.CompanyDto;
import com.meesho.gst.entity.Company;
import com.meesho.gst.service.CompanyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompanyControllerTest {

    @Mock
    private CompanyService companyService;

    @Mock
    private Authentication authentication;

    @Mock
    private OAuth2User principal;

    private CompanyController companyController;

    @BeforeEach
    void setUp() {
        companyController = new CompanyController(companyService);
    }

    @Test
    @DisplayName("GET /api/companies returns companies for the logged-in user only")
    void getCurrentUserCompanies_usesEmail() {
        Company company = new Company();
        company.setUserEmail("user@example.com");
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(principal.getAttribute("email")).thenReturn("user@example.com");
        when(companyService.getCompaniesForUser("user@example.com")).thenReturn(List.of(company));

        ResponseEntity<List<Company>> response = companyController.getCurrentUserCompanies(authentication);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().size());
        assertEquals("user@example.com", response.getBody().get(0).getUserEmail());
        verify(companyService).getCompaniesForUser("user@example.com");
    }

    @Test
    @DisplayName("POST /api/companies stores company for logged-in email")
    void createCompany_usesEmail() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(principal.getAttribute("email")).thenReturn("User@Example.com");
        CompanyDto dto = new CompanyDto();
        Company saved = new Company();
        when(companyService.createCompany(dto, "user@example.com")).thenReturn(saved);

        ResponseEntity<Company> response = companyController.createCompany(dto, authentication);
        assertEquals(201, response.getStatusCode().value());
        verify(companyService).createCompany(dto, "user@example.com");
    }

    @Test
    @DisplayName("DELETE /api/companies/{id} uses logged-in email for ownership check")
    void deleteCompany_usesEmail() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(principal.getAttribute("email")).thenReturn("user@example.com");

        ResponseEntity<Void> response = companyController.deleteCompany(3L, authentication);

        assertEquals(204, response.getStatusCode().value());
        verify(companyService).deleteCompany(3L, "user@example.com");
    }

    @Test
    @DisplayName("PUT /api/companies/{id}/default sets default profile for logged-in user")
    void setDefaultCompany_usesEmail() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(principal.getAttribute("email")).thenReturn("user@example.com");

        Company company = new Company();
        company.setId(5L);
        company.setDefault(true);
        when(companyService.setDefaultCompany(5L, "user@example.com")).thenReturn(company);

        ResponseEntity<Company> response = companyController.setDefaultCompany(5L, authentication);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isDefault());
        verify(companyService).setDefaultCompany(5L, "user@example.com");
    }

    @Test
    @DisplayName("Rejects company APIs when user is not logged in")
    void unauthenticated() {
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> companyController.getCurrentUserCompanies(null)
        );
        assertEquals(401, ex.getStatusCode().value());
    }
}
