package de.samply.manager.services;

import de.samply.manager.exception.ApiException;
import de.samply.manager.model.Document;
import de.samply.manager.model.HtmlLetterTemplate;
import de.samply.manager.model.Relationship;
import de.samply.manager.model.Share;
import de.samply.manager.repository.HtmlLetterTemplateRepository;
import de.samply.manager.repository.ShareRepository;
import de.samply.manager.security.AppRole;
import de.samply.manager.types.RelationshipStatus;
import de.samply.manager.types.SharedSubject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.support.StaticMessageSource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShareServiceTest {

    private static final String APPLICANT = "applicant-1";
    private static final String COUNTERPART = "advisor-1";
    private static final UUID REL_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID DOC_ID = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    @Mock ShareRepository shareRepository;
    @Mock RelationshipService relationshipService;
    @Mock DocumentService documentService;
    @Mock HtmlLetterTemplateRepository htmlLetterTemplateRepository;

    ShareService service;

    @BeforeEach
    void setUp() {
        StaticMessageSource messages = new StaticMessageSource();
        messages.setUseCodeAsDefaultMessage(true);
        service = new ShareService(shareRepository, relationshipService, documentService,
                htmlLetterTemplateRepository, messages);

        when(shareRepository.save(any(Share.class))).thenAnswer(i -> i.getArgument(0));
        when(shareRepository.findByRelationshipIdAndRevokedAtIsNull(REL_ID)).thenReturn(List.of());
    }

    private void relationshipIs(RelationshipStatus status) {
        when(relationshipService.findOwnedByApplicant(REL_ID, APPLICANT)).thenReturn(Relationship.builder()
                .id(REL_ID).applicantId(APPLICANT).counterpartId(COUNTERPART)
                .kind(AppRole.ADVISOR).status(status).build());
    }

    private static Document document() {
        return Document.builder().id(DOC_ID).userId(APPLICANT).label("CV").build();
    }

    @Test
    void grantingADocumentChecksOwnershipFirst() {
        relationshipIs(RelationshipStatus.ACTIVE);
        when(documentService.findOwned(DOC_ID, APPLICANT)).thenReturn(document());

        Share share = service.grant(REL_ID, APPLICANT, SharedSubject.DOCUMENT, DOC_ID);

        assertThat(share.getDocument().getId()).isEqualTo(DOC_ID);
        assertThat(share.isActive()).isTrue();
        verify(documentService).findOwned(DOC_ID, APPLICANT);
    }

    @Test
    void aDocumentTheApplicantDoesNotOwnCannotBeShared() {
        relationshipIs(RelationshipStatus.ACTIVE);
        when(documentService.findOwned(DOC_ID, APPLICANT)).thenThrow(new ApiException.Forbidden());

        assertThatThrownBy(() -> service.grant(REL_ID, APPLICANT, SharedSubject.DOCUMENT, DOC_ID))
                .isInstanceOf(ApiException.Forbidden.class);
        verify(shareRepository, never()).save(any());
    }

    @Test
    void grantingRequiresAnActiveRelationship() {
        relationshipIs(RelationshipStatus.REQUESTED);

        assertThatThrownBy(() -> service.grant(REL_ID, APPLICANT, SharedSubject.COMPANIES, null))
                .isInstanceOf(ApiException.Conflict.class);
        verify(shareRepository, never()).save(any());
    }

    @Test
    void onlyTheApplicantCanGrant() {
        when(relationshipService.findOwnedByApplicant(REL_ID, COUNTERPART))
                .thenThrow(new ApiException.Forbidden());

        assertThatThrownBy(() -> service.grant(REL_ID, COUNTERPART, SharedSubject.COMPANIES, null))
                .isInstanceOf(ApiException.Forbidden.class);
        verify(shareRepository, never()).save(any());
    }

    @Test
    void aPerResourceSubjectRequiresAResource() {
        relationshipIs(RelationshipStatus.ACTIVE);

        assertThatThrownBy(() -> service.grant(REL_ID, APPLICANT, SharedSubject.DOCUMENT, null))
                .isInstanceOf(ApiException.BadRequest.class);
    }

    @Test
    void aCategorySubjectRejectsAResource() {
        relationshipIs(RelationshipStatus.ACTIVE);

        assertThatThrownBy(() -> service.grant(REL_ID, APPLICANT, SharedSubject.APPLICATION_STATUS, DOC_ID))
                .isInstanceOf(ApiException.BadRequest.class);
    }

    @Test
    void aCategorySubjectIsGrantedWholesale() {
        relationshipIs(RelationshipStatus.ACTIVE);

        Share share = service.grant(REL_ID, APPLICANT, SharedSubject.APPLICATION_STATUS, null);

        assertThat(share.getSubjectType()).isEqualTo(SharedSubject.APPLICATION_STATUS);
        assertThat(share.getDocument()).isNull();
        assertThat(share.getHtmlLetterTemplate()).isNull();
    }

    @Test
    void theSameResourceCannotBeGrantedTwice() {
        relationshipIs(RelationshipStatus.ACTIVE);
        when(documentService.findOwned(DOC_ID, APPLICANT)).thenReturn(document());
        when(shareRepository.findByRelationshipIdAndRevokedAtIsNull(REL_ID)).thenReturn(List.of(
                Share.builder().subjectType(SharedSubject.DOCUMENT).document(document()).build()));

        assertThatThrownBy(() -> service.grant(REL_ID, APPLICANT, SharedSubject.DOCUMENT, DOC_ID))
                .isInstanceOf(ApiException.Conflict.class);
    }

    @Test
    void aLetterTemplateMustBelongToTheApplicant() {
        relationshipIs(RelationshipStatus.ACTIVE);
        when(htmlLetterTemplateRepository.findByIdAndUserId(DOC_ID, APPLICANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.grant(REL_ID, APPLICANT, SharedSubject.HTML_LETTER_TEMPLATE, DOC_ID))
                .isInstanceOf(ApiException.NotFound.class);
    }

    @Test
    void aLetterTemplateIsSharedWhenOwned() {
        relationshipIs(RelationshipStatus.ACTIVE);
        when(htmlLetterTemplateRepository.findByIdAndUserId(DOC_ID, APPLICANT)).thenReturn(
                Optional.of(HtmlLetterTemplate.builder().id(DOC_ID).userId(APPLICANT).name("Standard").build()));

        Share share = service.grant(REL_ID, APPLICANT, SharedSubject.HTML_LETTER_TEMPLATE, DOC_ID);

        assertThat(share.getHtmlLetterTemplate().getId()).isEqualTo(DOC_ID);
    }

    @Test
    void revokingMarksTheShareInactive() {
        relationshipIs(RelationshipStatus.ACTIVE);
        UUID shareId = UUID.randomUUID();
        Share share = Share.builder().id(shareId)
                .relationship(Relationship.builder().id(REL_ID).build()).build();
        when(shareRepository.findById(shareId)).thenReturn(Optional.of(share));

        service.revoke(REL_ID, shareId, APPLICANT);

        assertThat(share.isActive()).isFalse();
        verify(shareRepository).save(share);
    }

    @Test
    void aShareFromAnotherRelationshipCannotBeRevokedThroughThisOne() {
        relationshipIs(RelationshipStatus.ACTIVE);
        UUID shareId = UUID.randomUUID();
        when(shareRepository.findById(shareId)).thenReturn(Optional.of(Share.builder().id(shareId)
                .relationship(Relationship.builder().id(UUID.randomUUID()).build()).build()));

        assertThatThrownBy(() -> service.revoke(REL_ID, shareId, APPLICANT))
                .isInstanceOf(ApiException.NotFound.class);
    }

    @Test
    void hasActiveShareIsFalseWithoutAGrant() {
        when(shareRepository.findActiveForCounterpart(COUNTERPART, SharedSubject.DOCUMENT))
                .thenReturn(List.of());

        assertThat(service.hasActiveShare(COUNTERPART, SharedSubject.DOCUMENT, DOC_ID)).isFalse();
    }

    @Test
    void hasActiveShareIsTrueForTheGrantedResource() {
        when(shareRepository.findActiveForCounterpart(COUNTERPART, SharedSubject.DOCUMENT))
                .thenReturn(List.of(Share.builder()
                        .subjectType(SharedSubject.DOCUMENT).document(document()).build()));

        assertThat(service.hasActiveShare(COUNTERPART, SharedSubject.DOCUMENT, DOC_ID)).isTrue();
        assertThat(service.hasActiveShare(COUNTERPART, SharedSubject.DOCUMENT, UUID.randomUUID())).isFalse();
    }
}
