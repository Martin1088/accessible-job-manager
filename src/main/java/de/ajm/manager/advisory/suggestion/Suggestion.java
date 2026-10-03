package de.ajm.manager.advisory.suggestion;

import de.ajm.manager.company.CompanyPosition;
import de.ajm.manager.profile.UserProfile;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "suggestions")
@Getter
@Setter
public class Suggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String advisorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_profile_id")
    private UserProfile targetUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_position_id")
    private CompanyPosition companyPosition;

    private String message;

    @Enumerated(EnumType.STRING)
    private SuggestionStatus status;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
