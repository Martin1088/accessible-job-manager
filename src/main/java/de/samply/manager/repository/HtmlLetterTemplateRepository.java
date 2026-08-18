package de.samply.manager.repository;

import de.samply.manager.model.HtmlLetterTemplate;
import de.samply.manager.types.Language;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HtmlLetterTemplateRepository extends JpaRepository<HtmlLetterTemplate, UUID> {

    List<HtmlLetterTemplate> findByUserId(String userId);

    List<HtmlLetterTemplate> findByUserIdOrderByUpdatedAtDesc(String userId);

    List<HtmlLetterTemplate> findByUserIdAndLanguageOrderByUpdatedAtDesc(String userId, Language language);

    Optional<HtmlLetterTemplate> findByIdAndUserId(UUID id, String userId);

    boolean existsByUserId(String userId);
}
