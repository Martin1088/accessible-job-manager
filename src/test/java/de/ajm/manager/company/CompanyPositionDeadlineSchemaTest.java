package de.ajm.manager.company;

import de.ajm.manager.testing.PostgresDataJpaTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The application deadline is a day, stored as one. Run against PostgreSQL
 * because the column type is the point: the earlier {@code deadline} column
 * was a {@code timestamp}, which {@code ddl-auto: update} would never have
 * turned into a {@code date}, hence the new {@code application_deadline}.
 */
@PostgresDataJpaTest
class CompanyPositionDeadlineSchemaTest {

    @Autowired CompanyRepository companyRepository;
    @Autowired EntityManager entityManager;

    @Test
    void theDeadlineIsADateColumnAndSurvivesARoundTrip() {
        Company company = new Company();
        company.setUserId("u1");
        company.setName("Acme");
        CompanyPosition position = new CompanyPosition();
        position.setTitle("Developer");
        position.setDeadline(LocalDate.of(2026, 9, 30));
        position.setCompany(company);
        company.getPositions().add(position);
        companyRepository.saveAndFlush(company);
        entityManager.clear();

        CompanyPosition read = companyRepository.findById(company.getId()).orElseThrow()
                .getPositions().getFirst();
        assertThat(read.getDeadline()).isEqualTo(LocalDate.of(2026, 9, 30));

        Object type = entityManager.createNativeQuery("""
                select data_type from information_schema.columns
                where table_name = 'company_positions' and column_name = 'application_deadline'
                """).getSingleResult();
        assertThat(type).isEqualTo("date");
    }
}
