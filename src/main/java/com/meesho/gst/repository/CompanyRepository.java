package com.meesho.gst.repository;

import com.meesho.gst.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {
    boolean existsByGstin(String gstin);

    List<Company> findByUserEmailOrderByCreatedAtDesc(String userEmail);

    long countByUserEmail(String userEmail);

    Optional<Company> findByIdAndUserEmail(Long id, String userEmail);

    Optional<Company> findByUserEmailAndIsDefaultTrue(String userEmail);
}
