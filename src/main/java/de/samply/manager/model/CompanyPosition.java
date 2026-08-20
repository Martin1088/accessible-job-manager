package de.samply.manager.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.samply.manager.types.ApplicationMethod;
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

    @Enumerated(EnumType.STRING)
    private ApplicationMethod applicationMethod;

    private String email;
    @Column(length = 2048)
    private String website;
    private String notes;
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
