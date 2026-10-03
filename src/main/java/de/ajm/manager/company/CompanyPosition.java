package de.ajm.manager.company;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.ajm.manager.application.ApplicationMethod;
import de.ajm.manager.types.Gender;
import de.ajm.manager.types.Language;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@Setter
@ToString(exclude = "company")
@Entity
@EntityListeners(AuditingEntityListener.class)
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
    private LocalDateTime deadline;

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

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
