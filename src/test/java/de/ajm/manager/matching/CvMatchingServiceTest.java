package de.ajm.manager.matching;

import de.ajm.manager.coverletter.CoverLetterFixtures;
import de.ajm.manager.document.Document;
import de.ajm.manager.document.DocumentService;
import de.ajm.manager.document.DocumentType;
import de.ajm.manager.exception.ApiException;
import de.ajm.manager.jobimport.JobPostingParserService;
import de.ajm.manager.jobimport.PostingPdfTextExtractor;
import de.ajm.manager.jobimport.llm.JobPostingLlmClient;
import de.ajm.manager.jobimport.llm.LlmExtractionSpec;
import de.ajm.manager.matching.CvMatchSpec.RawMatch;
import de.ajm.manager.matching.CvMatchSpec.RawSkill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class CvMatchingServiceTest {

    private static final UUID CV_ID = UUID.fromString("00000000-0000-0000-0000-000000000007");
    private static final String POSTING = "Frontend Engineer. Required: Angular, WCAG 2.2, fluent German.";
    private static final String CV_TEXT = "Jane Doe. Angular applications since 2019.";
    private static final RawMatch ANSWER = new RawMatch("Frontend Engineer", "Aurum", "en", "en",
            List.of(new RawSkill("Angular", "TECHNICAL", "REQUIRED", "MET", "Angular", "Angular applications",
                    "Shown.", null)),
            "STRONG", "Fits.");

    private final DocumentService documentService = mock(DocumentService.class);
    private final JobPostingParserService parserService = mock(JobPostingParserService.class);
    private final PostingPdfTextExtractor pdfTextExtractor = mock(PostingPdfTextExtractor.class);
    private final JobPostingLlmClient client = mock(JobPostingLlmClient.class);

    private final CvMatchingService service = new CvMatchingService(documentService, parserService,
            pdfTextExtractor, new CvMatchingModel(client, "test model", true), CoverLetterFixtures.messageSource());

    private final Document cv = Document.builder().id(CV_ID).userId("u1").type(DocumentType.CV)
            .label("Lebenslauf 2026").build();

    @BeforeEach
    void cvAndModel() throws IOException {
        when(documentService.findOwned(CV_ID, "u1", DocumentType.CV)).thenReturn(cv);
        when(documentService.bytes(cv)).thenReturn(new byte[]{1});
        when(pdfTextExtractor.extract(any())).thenReturn(CV_TEXT);
        when(parserService.requirePostingText(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(client.extract(anyString(), any())).thenReturn(ANSWER);
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<LlmExtractionSpec<RawMatch>> specCaptor() {
        return ArgumentCaptor.forClass(LlmExtractionSpec.class);
    }

    @Test
    void readsTheLinkThroughTheParserAndHandsTheModelBothTexts() {
        when(parserService.renderedPostingText("https://jobs.example/1", "u1")).thenReturn(POSTING);

        CvMatchResult result = service.matchUrl("https://jobs.example/1", CV_ID, "de", "u1");

        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<LlmExtractionSpec<RawMatch>> spec = specCaptor();
        verify(client).extract(content.capture(), spec.capture());
        assertThat(content.getValue()).contains("JOB POSTING:", POSTING, "CV:", CV_TEXT);
        assertThat(spec.getValue().systemPrompt()).contains("in German");
        verify(parserService).requirePostingText(POSTING);

        assertThat(result.language()).isEqualTo("de");
        assertThat(result.cvDocumentId()).isEqualTo(CV_ID);
        assertThat(result.skills().getFirst().cvEvidence()).isEqualTo("Angular applications");
    }

    @Test
    void appliesThePostingTextRulesToPastedText() {
        when(parserService.requirePostingText("too short"))
                .thenThrow(new ApiException.BadRequest("Please paste at least 120 characters"));

        assertThatThrownBy(() -> service.matchText("too short", CV_ID, "en", "u1"))
                .isInstanceOf(ApiException.BadRequest.class);
        verifyNoInteractions(client);
    }

    @Test
    void checksTheCvBeforeFetchingThePosting() {
        when(documentService.findOwned(CV_ID, "u2", DocumentType.CV)).thenThrow(new ApiException.Forbidden());

        assertThatThrownBy(() -> service.matchUrl("https://jobs.example/1", CV_ID, "en", "u2"))
                .isInstanceOf(ApiException.Forbidden.class);
        verifyNoInteractions(parserService, client);
    }

    @Test
    void refusesAMissingCvId() {
        assertThatThrownBy(() -> service.matchText(POSTING, null, "en", "u1"))
                .isInstanceOf(ApiException.BadRequest.class)
                .hasMessageContaining("CV");
    }

    @Test
    void namesTheCvWhenNoTextCanBeReadFromIt() {
        when(pdfTextExtractor.extract(any())).thenThrow(new ApiException.BadRequest("a scan or a screenshot"));

        assertThatThrownBy(() -> service.matchText(POSTING, CV_ID, "en", "u1"))
                .isInstanceOf(ApiException.BadRequest.class)
                .hasMessageContaining("Lebenslauf 2026")
                .hasMessageNotContaining("screenshot");
    }

    @Test
    void cutsALongCv() {
        when(pdfTextExtractor.extract(any())).thenReturn("x".repeat(CvMatchingService.MAX_CV_CHARS + 500));

        service.matchText(POSTING, CV_ID, "en", "u1");

        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(client).extract(content.capture(), any());
        assertThat(content.getValue()).doesNotContain("x".repeat(CvMatchingService.MAX_CV_CHARS + 1));
    }

    @Test
    void reportsAnAnswerWithoutUsableRequirementsAsTheModelsFailure() {
        when(client.extract(anyString(), any()))
                .thenReturn(new RawMatch(null, null, null, null, List.of(), "STRONG", "Fits."));

        assertThatThrownBy(() -> service.matchText(POSTING, CV_ID, "en", "u1"))
                .isInstanceOf(ApiException.BadGateway.class);
    }

    @Test
    void answersInTheRequestedLanguageAndInEnglishWithoutOne() {
        assertThat(CvMatchingService.answerLanguage("DE")).isEqualTo("de");
        assertThat(CvMatchingService.answerLanguage("de-DE")).isEqualTo("de");
        assertThat(CvMatchingService.answerLanguage("deutsch")).isEqualTo("en");
        assertThat(CvMatchingService.answerLanguage(null)).isEqualTo("en");
    }
}
