package de.samply.manager.model;

import de.samply.manager.types.Language;
import de.samply.manager.types.Role;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "user_profiles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {

    @Id
    private String userId;

    @Enumerated(EnumType.STRING)
    private Role role;
    private String name;
    private String email;

    private String street;
    private String postalCode;
    private String city;
    private String phone;

    @Enumerated(EnumType.STRING)
    private Language language;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @ManyToMany
    @JoinTable(
            name = "user_advisors",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "advisor_id")
    )
    @Builder.Default
    private Set<UserProfile> advisors = new HashSet<>();

    @ManyToMany
    @JoinTable(
            name = "user_reviewers",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "reviewer_id")
    )
    @Builder.Default
    private Set<UserProfile> reviewers = new HashSet<>();

    @Embedded
    @Builder.Default
    private UserPreferences preferences = new UserPreferences();

    public UserPreferences getPreferences() {
        return preferences != null ? preferences : new UserPreferences();
    }
}
