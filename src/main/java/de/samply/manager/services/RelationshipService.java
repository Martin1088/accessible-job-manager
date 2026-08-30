package de.samply.manager.services;

import de.samply.manager.exception.ApiException;
import de.samply.manager.model.Relationship;
import de.samply.manager.model.Share;
import de.samply.manager.model.UserProfile;
import de.samply.manager.repository.RelationshipRepository;
import de.samply.manager.repository.ShareRepository;
import de.samply.manager.repository.UserProfileRepository;
import de.samply.manager.security.AppRole;
import de.samply.manager.types.RelationshipStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RelationshipService {

    private static final Set<AppRole> SHAREABLE_KINDS = EnumSet.of(AppRole.ADVISOR, AppRole.REVIEWER);
    private static final Set<RelationshipStatus> NON_TERMINAL =
            EnumSet.of(RelationshipStatus.REQUESTED, RelationshipStatus.ACTIVE);

    private final RelationshipRepository relationshipRepository;
    private final ShareRepository shareRepository;
    private final UserProfileRepository userProfileRepository;
    private final MessageSource messageSource;

    @Transactional
    public Relationship request(String applicantId, String counterpartId, AppRole kind) {
        if (kind == null || !SHAREABLE_KINDS.contains(kind)) {
            throw new ApiException.BadRequest(message("error.relationship.kindNotShareable"));
        }
        if (applicantId.equals(counterpartId)) {
            throw new ApiException.BadRequest(message("error.relationship.selfRequest"));
        }

        UserProfile counterpart = userProfileRepository.findById(counterpartId)
                .orElseThrow(() -> new ApiException.NotFound(
                        message("error.profile.userNotFound", counterpartId)));
        if (!counterpart.getRoles().contains(kind)) {
            throw new ApiException.NotFound(
                    message("error.relationship.counterpartNotEligible", counterpartId, kind.name()));
        }

        if (relationshipRepository.existsByApplicantIdAndCounterpartIdAndKindAndStatusIn(
                applicantId, counterpartId, kind, NON_TERMINAL)) {
            throw new ApiException.Conflict(message("error.relationship.alreadyExists"));
        }

        return relationshipRepository.save(Relationship.builder()
                .applicantId(applicantId)
                .counterpartId(counterpartId)
                .kind(kind)
                .status(RelationshipStatus.REQUESTED)
                .build());
    }

    @Transactional
    public Relationship accept(UUID relationshipId, String counterpartId) {
        return answer(relationshipId, counterpartId, RelationshipStatus.ACTIVE);
    }

    @Transactional
    public Relationship decline(UUID relationshipId, String counterpartId) {
        return answer(relationshipId, counterpartId, RelationshipStatus.DECLINED);
    }

    private Relationship answer(UUID relationshipId, String counterpartId, RelationshipStatus outcome) {
        Relationship relationship = find(relationshipId);
        if (!relationship.getCounterpartId().equals(counterpartId)) {
            throw new ApiException.Forbidden();
        }
        if (relationship.getStatus() != RelationshipStatus.REQUESTED) {
            throw new ApiException.Conflict(message("error.relationship.notRequested"));
        }
        relationship.setStatus(outcome);
        return relationshipRepository.save(relationship);
    }

    @Transactional
    public Relationship end(UUID relationshipId, String callerId) {
        Relationship relationship = find(relationshipId);
        if (!relationship.getApplicantId().equals(callerId)
                && !relationship.getCounterpartId().equals(callerId)) {
            throw new ApiException.Forbidden();
        }
        if (relationship.getStatus() != RelationshipStatus.ACTIVE) {
            throw new ApiException.Conflict(message("error.relationship.notActive"));
        }

        LocalDateTime now = LocalDateTime.now();
        List<Share> active = shareRepository.findByRelationshipIdAndRevokedAtIsNull(relationshipId);
        active.forEach(share -> share.setRevokedAt(now));
        shareRepository.saveAll(active);

        relationship.setStatus(RelationshipStatus.ENDED);
        return relationshipRepository.save(relationship);
    }

    @Transactional(readOnly = true)
    public List<Relationship> forApplicant(String applicantId) {
        return relationshipRepository.findByApplicantId(applicantId);
    }

    @Transactional(readOnly = true)
    public List<Relationship> forCounterpart(String counterpartId) {
        return relationshipRepository.findByCounterpartId(counterpartId);
    }

    @Transactional(readOnly = true)
    public List<Relationship> activeFor(String counterpartId, AppRole kind) {
        return relationshipRepository.findByCounterpartIdAndKindAndStatus(
                counterpartId, kind, RelationshipStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public Relationship findOwnedByApplicant(UUID relationshipId, String applicantId) {
        Relationship relationship = find(relationshipId);
        if (!relationship.getApplicantId().equals(applicantId)) {
            throw new ApiException.Forbidden();
        }
        return relationship;
    }

    @Transactional(readOnly = true)
    public Relationship findParticipant(UUID relationshipId, String callerId) {
        Relationship relationship = find(relationshipId);
        if (!relationship.getApplicantId().equals(callerId)
                && !relationship.getCounterpartId().equals(callerId)) {
            throw new ApiException.Forbidden();
        }
        return relationship;
    }

    private Relationship find(UUID relationshipId) {
        return relationshipRepository.findById(relationshipId)
                .orElseThrow(() -> new ApiException.NotFound(message("error.relationship.notFound")));
    }

    private String message(String key, Object... args) {
        return messageSource.getMessage(key, args, Locale.ROOT);
    }
}
