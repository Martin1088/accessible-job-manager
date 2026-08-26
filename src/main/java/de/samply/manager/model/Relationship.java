package de.samply.manager.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import de.samply.manager.security.AppRole;
import de.samply.manager.types.RelationshipStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "relationship")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Relationship {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String applicantId;

    @Column(nullable = false)
    private String counterpartId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppRole kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RelationshipStatus status;

    @OneToMany(mappedBy = "relationship", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Share> sharedResources = new ArrayList<>();

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
