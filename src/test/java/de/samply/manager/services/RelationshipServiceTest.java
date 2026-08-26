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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.support.StaticMessageSource;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RelationshipServiceTest {

    private static final String APPLICANT = "applicant-1";
    private static final String COUNTERPART = "advisor-1";
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    @Mock RelationshipRepository relationshipRepository;
    @Mock ShareRepository shareRepository;
    @Mock UserProfileRepository userProfileRepository;

    RelationshipService service;

    @BeforeEach
    void setUp() {
        StaticMessageSource messages = new StaticMessageSource();
        messages.setUseCodeAsDefaultMessage(true);
        service = new RelationshipService(relationshipRepository, shareRepository, userProfileRepository, messages);

        when(relationshipRepository.save(any(Relationship.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void counterpartHolds(AppRole... roles) {
        when(userProfileRepository.findById(COUNTERPART)).thenReturn(Optional.of(
                UserProfile.builder().userId(COUNTERPART).roles(Set.of(roles)).build()));
    }

    private static Relationship relationship(RelationshipStatus status) {
        return Relationship.builder()
                .id(ID)
                .applicantId(APPLICANT)
                .counterpartId(COUNTERPART)
                .kind(AppRole.ADVISOR)
                .status(status)
                .build();
    }

    @Test
    void requestCreatesARequestedRelationship() {
        counterpartHolds(AppRole.ADVISOR);

        Relationship created = service.request(APPLICANT, COUNTERPART, AppRole.ADVISOR);

        assertThat(created.getStatus()).isEqualTo(RelationshipStatus.REQUESTED);
        assertThat(created.getApplicantId()).isEqualTo(APPLICANT);
        assertThat(created.getCounterpartId()).isEqualTo(COUNTERPART);
    }

    @Test
    void aRelationshipCannotBeRequestedWithAPlainUser() {
        assertThatThrownBy(() -> service.request(APPLICANT, COUNTERPART, AppRole.USER))
                .isInstanceOf(ApiException.BadRequest.class);
        verify(relationshipRepository, never()).save(any());
    }

    @Test
    void aRelationshipCannotBeRequestedWithYourself() {
        assertThatThrownBy(() -> service.request(APPLICANT, APPLICANT, AppRole.ADVISOR))
                .isInstanceOf(ApiException.BadRequest.class);
        verify(relationshipRepository, never()).save(any());
    }

    @Test
    void theCounterpartMustActuallyHoldTheRole() {
        counterpartHolds(AppRole.USER);

        assertThatThrownBy(() -> service.request(APPLICANT, COUNTERPART, AppRole.ADVISOR))
                .isInstanceOf(ApiException.NotFound.class);
        verify(relationshipRepository, never()).save(any());
    }

    @Test
    void anUnknownCounterpartIsRejected() {
        when(userProfileRepository.findById(COUNTERPART)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.request(APPLICANT, COUNTERPART, AppRole.ADVISOR))
                .isInstanceOf(ApiException.NotFound.class);
    }

    @Test
    void aSecondNonTerminalRequestIsRejected() {
        counterpartHolds(AppRole.ADVISOR);
        when(relationshipRepository.existsByApplicantIdAndCounterpartIdAndKindAndStatusIn(
                anyString(), anyString(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.request(APPLICANT, COUNTERPART, AppRole.ADVISOR))
                .isInstanceOf(ApiException.Conflict.class);
    }

    @Test
    void theCounterpartAccepts() {
        when(relationshipRepository.findById(ID)).thenReturn(Optional.of(relationship(RelationshipStatus.REQUESTED)));

        assertThat(service.accept(ID, COUNTERPART).getStatus()).isEqualTo(RelationshipStatus.ACTIVE);
    }

    @Test
    void theApplicantCannotAcceptTheirOwnRequest() {
        when(relationshipRepository.findById(ID)).thenReturn(Optional.of(relationship(RelationshipStatus.REQUESTED)));

        assertThatThrownBy(() -> service.accept(ID, APPLICANT))
                .isInstanceOf(ApiException.Forbidden.class);
    }

    @Test
    void aStrangerCannotAccept() {
        when(relationshipRepository.findById(ID)).thenReturn(Optional.of(relationship(RelationshipStatus.REQUESTED)));

        assertThatThrownBy(() -> service.accept(ID, "someone-else"))
                .isInstanceOf(ApiException.Forbidden.class);
    }

    @Test
    void anAlreadyActiveRelationshipCannotBeAcceptedAgain() {
        when(relationshipRepository.findById(ID)).thenReturn(Optional.of(relationship(RelationshipStatus.ACTIVE)));

        assertThatThrownBy(() -> service.accept(ID, COUNTERPART))
                .isInstanceOf(ApiException.Conflict.class);
    }

    @Test
    void theCounterpartDeclines() {
        when(relationshipRepository.findById(ID)).thenReturn(Optional.of(relationship(RelationshipStatus.REQUESTED)));

        assertThat(service.decline(ID, COUNTERPART).getStatus()).isEqualTo(RelationshipStatus.DECLINED);
    }

    @Test
    void endingRevokesEveryStillActiveShare() {
        when(relationshipRepository.findById(ID)).thenReturn(Optional.of(relationship(RelationshipStatus.ACTIVE)));
        Share share = Share.builder().id(UUID.randomUUID()).build();
        when(shareRepository.findByRelationshipIdAndRevokedAtIsNull(ID)).thenReturn(List.of(share));

        Relationship ended = service.end(ID, APPLICANT);

        assertThat(ended.getStatus()).isEqualTo(RelationshipStatus.ENDED);
        assertThat(share.getRevokedAt()).isNotNull();
        assertThat(share.isActive()).isFalse();
        verify(shareRepository).saveAll(List.of(share));
    }

    @Test
    void eitherPartyMayEndAnActiveRelationship() {
        when(relationshipRepository.findById(ID)).thenReturn(Optional.of(relationship(RelationshipStatus.ACTIVE)));
        when(shareRepository.findByRelationshipIdAndRevokedAtIsNull(ID)).thenReturn(List.of());

        assertThat(service.end(ID, COUNTERPART).getStatus()).isEqualTo(RelationshipStatus.ENDED);
    }

    @Test
    void aStrangerCannotEndARelationship() {
        when(relationshipRepository.findById(ID)).thenReturn(Optional.of(relationship(RelationshipStatus.ACTIVE)));

        assertThatThrownBy(() -> service.end(ID, "someone-else"))
                .isInstanceOf(ApiException.Forbidden.class);
    }

    @Test
    void findOwnedByApplicantRefusesTheCounterpart() {
        when(relationshipRepository.findById(ID)).thenReturn(Optional.of(relationship(RelationshipStatus.ACTIVE)));

        assertThatThrownBy(() -> service.findOwnedByApplicant(ID, COUNTERPART))
                .isInstanceOf(ApiException.Forbidden.class);
    }

    @Test
    void anUnknownRelationshipIsNotFound() {
        when(relationshipRepository.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.accept(ID, COUNTERPART))
                .isInstanceOf(ApiException.NotFound.class);
    }
}
