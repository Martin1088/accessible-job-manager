package de.samply.manager.repository;

import de.samply.manager.model.CompanyPosition;
import de.samply.manager.types.TriageState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CompanyPositionRepository extends JpaRepository<CompanyPosition, Long> {
    List<CompanyPosition> findByCompanyId(Long companyId);

    /**
     * The queue, oldest first: what came in first has been waiting longest, and
     * a queue that reorders itself under the reader is hard to work through.
     * Filtered by the owning company's userId, so the isolation rule holds here
     * as it does everywhere else - a position is only ever visible to the
     * subject its company belongs to.
     */
    List<CompanyPosition> findByCompanyUserIdAndTriageStateOrderByCreatedAtAsc(String userId, TriageState triageState);

    long countByCompanyUserIdAndTriageState(String userId, TriageState triageState);
}
