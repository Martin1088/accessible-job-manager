package de.samply.manager.model;

import de.samply.manager.types.Gender;
import de.samply.manager.types.Language;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@ToString(exclude = "company")
@EqualsAndHashCode(exclude = "company")
@Entity
@Table(name = "company_positions")
public class CompanyPosition {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Enumerated(EnumType.STRING)
    private Gender contactGender;
    private String contactTitle;
    private String contactLastName;

    @Enumerated(EnumType.STRING)
    private Language applyLanguage;

    private String email;
    private String website;
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
