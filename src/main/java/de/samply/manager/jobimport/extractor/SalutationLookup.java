package de.samply.manager.jobimport.extractor;

import de.samply.manager.types.Gender;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Salutation word -> Gender, loaded from jobimport/salutations.properties so
 * new languages/forms can be added without touching code.
 */
@Component
class SalutationLookup {

    private final Map<String, Gender> byWord;
    private final String alternation; // longest word first, for regex embedding

    SalutationLookup() {
        Properties props = new Properties();
        try (var in = new InputStreamReader(
                new ClassPathResource("jobimport/salutations.properties").getInputStream(),
                StandardCharsets.UTF_8)) {
            props.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Could not load jobimport/salutations.properties", e);
        }

        Map<String, Gender> map = new LinkedHashMap<>();
        for (String word : props.stringPropertyNames()) {
            map.put(word, Gender.valueOf(props.getProperty(word).trim()));
        }
        this.byWord = map;
        this.alternation = map.keySet().stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .map(Pattern::quote)
                .reduce((a, b) -> a + "|" + b)
                .orElseThrow(() -> new IllegalStateException("jobimport/salutations.properties is empty"));
    }

    /** Regex alternation of all known salutation words, for embedding in a larger pattern. */
    String wordsPattern() {
        return alternation;
    }

    Optional<Gender> gender(String word) {
        return Optional.ofNullable(byWord.get(word));
    }
}
