package de.ajm.manager.matching;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import de.ajm.manager.coverletter.CoverLetterFixtures;
import de.ajm.manager.jobimport.llm.OllamaJobPostingLlmClient;
import de.ajm.manager.matching.CvMatchSpec.RawMatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The spec through the real Ollama client against a fake server: the schema that goes
 * out, and an answer shaped the way a model writes one mapped onto {@link RawMatch} -
 * nested list, an enum value written wrong, a field the schema does not have.
 */
class CvMatchSpecTest {

    private static final String MODEL_ANSWER = """
            {"postingTitle":"Frontend Engineer","company":"Aurum","postingLanguage":"de","cvLanguage":"en",
             "skills":[
               {"skill":"Angular","category":"TECHNICAL","importance":"REQUIRED","status":"MET",
                "postingQuote":"Angular","cvEvidence":"Angular applications","reasoning":"Shown.","suggestion":null,
                "confidence":0.9},
               {"skill":"German","category":"LANGUAGE","importance":"REQUIRED","status":"FLUENT",
                "postingQuote":null,"cvEvidence":null,"reasoning":"?","suggestion":null}],
             "level":"PARTIAL","summary":"Mostly there."}""";

    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicReference<JsonNode> sent = new AtomicReference<>();
    private HttpServer server;

    @BeforeEach
    void fakeOllama() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/chat", exchange -> {
            sent.set(mapper.readTree(exchange.getRequestBody()));
            byte[] answer = mapper.writeValueAsBytes(
                    mapper.createObjectNode().set("message", mapper.createObjectNode().put("content", MODEL_ANSWER)));
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, answer.length);
            exchange.getResponseBody().write(answer);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private RawMatch ask() {
        return new OllamaJobPostingLlmClient("http://127.0.0.1:" + server.getAddress().getPort(), "qwen2.5:14b",
                5, 10, 4000, 16384, mapper, CoverLetterFixtures.messageSource())
                .extract(CvMatchSpec.userContent("posting", "cv"), CvMatchSpec.forLanguage("de", true));
    }

    @Test
    void mapsAModelsAnswerOntoTheRawRecords() {
        RawMatch raw = ask();

        assertThat(raw.level()).isEqualTo("PARTIAL");
        assertThat(raw.skills()).hasSize(2);
        assertThat(raw.skills().getFirst().cvEvidence()).isEqualTo("Angular applications");
        // Kept as written here; the guard is what drops it.
        assertThat(raw.skills().get(1).status()).isEqualTo("FLUENT");
    }

    @Test
    void asksForTheNestedSchemaWithEveryFieldRequired() {
        ask();

        JsonNode format = sent.get().path("format");
        assertThat(format.path("required")).extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("postingTitle", "company", "postingLanguage", "cvLanguage",
                        "skills", "level", "summary");
        JsonNode skill = format.path("properties").path("skills").path("items");
        assertThat(skill.path("additionalProperties").asBoolean(true)).isFalse();
        assertThat(skill.path("required")).hasSize(8);
        assertThat(skill.path("properties").path("status").path("enum")).extracting(JsonNode::asText)
                .containsExactly("MET", "PARTIAL", "MISSING");
    }

    @Test
    void writesTheRequirementsBeforeTheVerdict() {
        ask();

        List<String> order = List.copyOf(sent.get().path("format").path("properties").properties()
                .stream().map(java.util.Map.Entry::getKey).toList());
        assertThat(order.indexOf("skills")).isLessThan(order.indexOf("level"));
        assertThat(order.indexOf("level")).isLessThan(order.indexOf("summary"));
    }

    @Test
    void namesTheAnswerLanguageAndFencesBothTexts() {
        ask();

        JsonNode messages = sent.get().path("messages");
        assertThat(messages.path(0).path("content").asText()).contains("in German");
        assertThat(messages.path(1).path("content").asText())
                .isEqualTo("JOB POSTING:\n<<<\nposting\n>>>\n\nCV:\n<<<\ncv\n>>>");
    }
}
