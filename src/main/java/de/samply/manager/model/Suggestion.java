package de.samply.manager.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "suggestions")
@Data
public class Suggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    @CreationTimestamp
    private LocalDateTime createdAt;
}
