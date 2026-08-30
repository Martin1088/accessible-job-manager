package de.samply.manager.advisory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SuggestionRepository extends JpaRepository<Suggestion, Long> {
    List<Suggestion> findByTargetUserUserId(String userId);
    List<Suggestion> findByAdvisorId(String advisorId);
}
