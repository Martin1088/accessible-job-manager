package de.samply.manager.repository;

import de.samply.manager.model.CompanyPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CompanyPositionRepository extends JpaRepository<CompanyPosition, Long> {
    List<CompanyPosition> findByCompanyId(Long companyId);
}
