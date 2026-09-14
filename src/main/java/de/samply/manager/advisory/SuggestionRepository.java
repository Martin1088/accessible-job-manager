package de.samply.manager.advisory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface SuggestionRepository extends JpaRepository<Suggestion, Long> {
    List<Suggestion> findByTargetUserUserId(String userId);
    List<Suggestion> findByAdvisorId(String advisorId);

    /**
     * Used to clear {@code suggestions.company_position_id} before the positions
     * are deleted. The suggestions go rather than being detached: the advisor's
     * list reads {@code suggestion.getCompanyPosition().getCompany().getName()}
     * with no null check, so a suggestion pointing at nothing is not a survivable
     * state.
     */
    List<Suggestion> findByCompanyPositionIdIn(Collection<Long> companyPositionIds);
}
