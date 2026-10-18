package com.meesho.gst.service;

import com.meesho.gst.dto.CompanyDto;
import com.meesho.gst.entity.Company;
import com.meesho.gst.exception.ForbiddenException;
import com.meesho.gst.exception.ResourceAlreadyExistsException;
import com.meesho.gst.exception.ResourceNotFoundException;
import com.meesho.gst.repository.CompanyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompanyServiceTest {

    @Mock
    private CompanyRepository companyRepository;

    private CompanyService companyService;

    @BeforeEach
    void setUp() {
        companyService = new CompanyService(companyRepository);
    }

    @Test
    @DisplayName("Creates company for logged-in user email and makes it default if it is the first")
    void createCompany_storesUserEmail() {
        CompanyDto dto = companyDto();
        when(companyRepository.countByUserEmail("user@example.com")).thenReturn(0L);
        when(companyRepository.existsByGstin(dto.getGstin())).thenReturn(false);
        when(companyRepository.save(any(Company.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Company saved = companyService.createCompany(dto, "User@Example.com");

        ArgumentCaptor<Company> captor = ArgumentCaptor.forClass(Company.class);
        verify(companyRepository).save(captor.capture());
        assertEquals("user@example.com", captor.getValue().getUserEmail());
        assertTrue(captor.getValue().isDefault());
        assertEquals(dto.getCompanyName(), saved.getCompanyName());
    }

    @Test
    @DisplayName("Second company added is not set as default automatically")
    void createCompany_secondCompanyNotDefault() {
        CompanyDto dto = companyDto();
        when(companyRepository.countByUserEmail("user@example.com")).thenReturn(1L);
        when(companyRepository.existsByGstin(dto.getGstin())).thenReturn(false);
        when(companyRepository.save(any(Company.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Company saved = companyService.createCompany(dto, "user@example.com");

        ArgumentCaptor<Company> captor = ArgumentCaptor.forClass(Company.class);
        verify(companyRepository).save(captor.capture());
        assertFalse(captor.getValue().isDefault());
    }

    @Test
    @DisplayName("Sets specified company as default and unsets previous default")
    void setDefaultCompany_updatesDefaults() {
        Company c1 = new Company();
        c1.setId(1L);
        c1.setUserEmail("user@example.com");
        c1.setDefault(true);

        Company c2 = new Company();
        c2.setId(2L);
        c2.setUserEmail("user@example.com");
        c2.setDefault(false);

        when(companyRepository.findById(2L)).thenReturn(Optional.of(c2));
        when(companyRepository.findByUserEmailOrderByCreatedAtDesc("user@example.com"))
                .thenReturn(List.of(c1, c2));

        Company updated = companyService.setDefaultCompany(2L, "user@example.com");

        assertTrue(updated.isDefault());
        assertFalse(c1.isDefault());
        assertTrue(c2.isDefault());
        verify(companyRepository).saveAll(List.of(c1, c2));
    }

    @Test
    @DisplayName("Rejects more than 10 companies for the same user")
    void createCompany_enforcesLimit() {
        when(companyRepository.countByUserEmail("user@example.com"))
                .thenReturn((long) CompanyService.MAX_COMPANIES_PER_USER);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> companyService.createCompany(companyDto(), "user@example.com")
        );
        assertTrue(ex.getMessage().contains("at most 10"));
        verify(companyRepository, never()).save(any());
    }

    @Test
    @DisplayName("Rejects duplicate GSTIN")
    void createCompany_duplicateGstin() {
        CompanyDto dto = companyDto();
        when(companyRepository.countByUserEmail("user@example.com")).thenReturn(1L);
        when(companyRepository.existsByGstin(dto.getGstin())).thenReturn(true);

        assertThrows(ResourceAlreadyExistsException.class,
                () -> companyService.createCompany(dto, "user@example.com"));
    }

    @Test
    @DisplayName("Lists only companies for the logged-in user")
    void getCompaniesForUser() {
        when(companyRepository.findByUserEmailOrderByCreatedAtDesc("user@example.com"))
                .thenReturn(List.of(new Company()));

        List<Company> result = companyService.getCompaniesForUser("USER@example.com");
        assertEquals(1, result.size());
        verify(companyRepository).findByUserEmailOrderByCreatedAtDesc("user@example.com");
    }

    @Test
    @DisplayName("Deletes only a company owned by the logged-in user")
    void deleteCompany_owned() {
        Company company = new Company();
        company.setId(3L);
        company.setUserEmail("user@example.com");
        when(companyRepository.findById(3L)).thenReturn(Optional.of(company));

        companyService.deleteCompany(3L, "user@example.com");
        verify(companyRepository).delete(company);
    }

    @Test
    @DisplayName("Does not delete another user's profile")
    void deleteCompany_notOwned() {
        Company company = new Company();
        company.setId(3L);
        company.setUserEmail("other@example.com");
        when(companyRepository.findById(3L)).thenReturn(Optional.of(company));

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> companyService.deleteCompany(3L, "user@example.com"));
        assertEquals("You can only delete profiles you added", ex.getMessage());
        verify(companyRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Returns not found when profile id does not exist")
    void deleteCompany_missing() {
        when(companyRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> companyService.deleteCompany(99L, "user@example.com"));
        verify(companyRepository, never()).delete(any());
    }

    private CompanyDto companyDto() {
        CompanyDto dto = new CompanyDto();
        dto.setCompanyName("Acme");
        dto.setOwnerName("Ada");
        dto.setGstin("29AABCU9603R1ZX");
        return dto;
    }
}
