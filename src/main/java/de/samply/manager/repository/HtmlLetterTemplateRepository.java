package de.samply.manager.repository;

import de.samply.manager.model.HtmlLetterTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface HtmlLetterTemplateRepository extends JpaRepository<HtmlLetterTemplate, UUID> {
    List<HtmlLetterTemplate> findByUserId(String userId);
}
