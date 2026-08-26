package de.samply.manager.model;

import de.samply.manager.security.AppRole;
import de.samply.manager.types.Language;
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
    private String name;
    private String email;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_profile_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    @Builder.Default
    private Set<AppRole> roles = new HashSet<>();

    private String street;
    private String postalCode;
    private String city;
    private String phone;

    @Enumerated(EnumType.STRING)
    private Language language;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @Embedded
    @Builder.Default
    private UserPreferences preferences = new UserPreferences();

    public UserPreferences getPreferences() {
        return preferences != null ? preferences : new UserPreferences();
    }
}
