package de.samply.manager.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import de.samply.manager.types.ApplicationMethod;
import de.samply.manager.types.Gender;
import de.samply.manager.types.Language;
import de.samply.manager.types.TriageState;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.ColumnDefault;
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
    private LocalDateTime deadline;

    @Enumerated(EnumType.STRING)
    private Language applyLanguage;

    @Enumerated(EnumType.STRING)
    private ApplicationMethod applicationMethod;

    /**
     * Two different defaults, which is the whole trick of adding this column to
     * a table that already has rows:
     *
     * <ul>
     *   <li>The Java field starts as {@code NEW}, so a position created from
     *       here on has to be looked at before it counts as part of the
     *       catalogue. {@link de.samply.manager.services.CompanyService} keeps
     *       the value on update, so this only ever applies to new rows.</li>
     *   <li>{@code @ColumnDefault} writes {@code ACCEPTED} into the DDL, which
     *       is what existing rows get when the column is added. Everything
     *       entered before this feature existed was entered deliberately and
     *       must not suddenly appear in a queue.</li>
     * </ul>
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @ColumnDefault("'ACCEPTED'")
    private TriageState triageState = TriageState.NEW;

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
