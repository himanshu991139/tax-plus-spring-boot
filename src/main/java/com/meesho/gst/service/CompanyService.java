package com.meesho.gst.service;

import com.meesho.gst.dto.CompanyDto;
import com.meesho.gst.entity.Company;
import com.meesho.gst.exception.ForbiddenException;
import com.meesho.gst.exception.ResourceAlreadyExistsException;
import com.meesho.gst.exception.ResourceNotFoundException;
import com.meesho.gst.repository.CompanyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CompanyService {

    static final int MAX_COMPANIES_PER_USER = 10;

    private final CompanyRepository companyRepository;

    @Autowired
    public CompanyService(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    @Transactional
    public Company createCompany(CompanyDto dto, String userEmail) {
        String email = normalizeEmail(userEmail);
        if (companyRepository.countByUserEmail(email) >= MAX_COMPANIES_PER_USER) {
            throw new IllegalArgumentException(
                    "You can add at most " + MAX_COMPANIES_PER_USER + " companies"
            );
        }
        if (companyRepository.existsByGstin(dto.getGstin())) {
            throw new ResourceAlreadyExistsException("Company with GSTIN " + dto.getGstin() + " already exists");
        }
        Company company = new Company();
        company.setCompanyName(dto.getCompanyName());
        company.setGstin(dto.getGstin());
        company.setUserEmail(email);

        // If this is the user's first company, make it default automatically
        long existingCount = companyRepository.countByUserEmail(email);
        if (existingCount == 0) {
            company.setDefault(true);
        }

        return companyRepository.save(company);
    }

    public List<Company> getCompaniesForUser(String userEmail) {
        return companyRepository.findByUserEmailOrderByCreatedAtDesc(normalizeEmail(userEmail));
    }

    @Transactional
    public Company setDefaultCompany(Long id, String userEmail) {
        String email = normalizeEmail(userEmail);
        Company target = companyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Profile with ID " + id + " does not exist"));

        if (target.getUserEmail() == null || !email.equals(target.getUserEmail().trim().toLowerCase())) {
            throw new ForbiddenException("You can only set default profile for profiles you added");
        }

        List<Company> userCompanies = companyRepository.findByUserEmailOrderByCreatedAtDesc(email);
        for (Company c : userCompanies) {
            if (c.getId().equals(id)) {
                c.setDefault(true);
            } else if (c.isDefault()) {
                c.setDefault(false);
            }
        }
        companyRepository.saveAll(userCompanies);
        target.setDefault(true);
        return target;
    }

    @Transactional
    public void deleteCompany(Long id, String userEmail) {
        String email = normalizeEmail(userEmail);
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Profile with ID " + id + " does not exist"));

        if (company.getUserEmail() == null || !email.equals(company.getUserEmail().trim().toLowerCase())) {
            throw new ForbiddenException("You can only delete profiles you added");
        }

        boolean wasDefault = company.isDefault();
        companyRepository.delete(company);

        // If the deleted profile was default, make the most recently created remaining profile the default
        if (wasDefault) {
            List<Company> remaining = companyRepository.findByUserEmailOrderByCreatedAtDesc(email);
            if (!remaining.isEmpty()) {
                Company nextDefault = remaining.get(0);
                nextDefault.setDefault(true);
                companyRepository.save(nextDefault);
            }
        }
    }

    private String normalizeEmail(String userEmail) {
        if (userEmail == null || userEmail.isBlank()) {
            throw new IllegalArgumentException("Logged-in user email is required");
        }
        return userEmail.trim().toLowerCase();
    }
}
