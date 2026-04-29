package de.samply.manager.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

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

    // Contact person (Ansprechpartner)
    @Enumerated(EnumType.STRING)
    private Gender contactGender;
    private String contactTitle;
    private String contactLastName;

    private String email;
    private String website;
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;
}
