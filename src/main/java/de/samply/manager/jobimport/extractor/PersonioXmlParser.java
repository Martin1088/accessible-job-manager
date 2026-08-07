package de.samply.manager.jobimport.extractor;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Parser for the Personio XML feed
 * (https://{subdomain}.jobs.personio.com/xml). Jsoup can read XML - no
 * extra XML stack needed, the project already has Jsoup on the classpath
 * for the JSON-LD tier.
 *
 * Feed schema (excerpt):
 *   <position>
 *     <id>12345</id>
 *     <name>Backend Engineer (m/w/d)</name>
 *     <office>Munich</office>
 *     <jobDescriptions>
 *       <jobDescription><name>Responsibilities</name><value><![CDATA[...]]></value></jobDescription>
 *       ...
 *     </jobDescriptions>
 *   </position>
 *
 * description stays raw (HTML from the CDATA) - like all other tiers, it's
 * not cleaned here, only later by the upstream PII pre-processing step.
 */
@Component
public class PersonioXmlParser {

    public record Posting(String name, String office, String description) {}

    public Optional<Posting> findPosting(String feedXml, String jobId) {
        if (feedXml == null || feedXml.isBlank() || jobId == null) {
            return Optional.empty();
        }
        Document doc = Jsoup.parse(feedXml, "", Parser.xmlParser());
        for (Element position : doc.select("position")) {
            if (jobId.equals(childText(position, "id"))) {
                return Optional.of(new Posting(
                        childText(position, "name"),
                        childText(position, "office"),
                        description(position)));
            }
        }
        return Optional.empty();
    }

    private String description(Element position) {
        StringBuilder sb = new StringBuilder();
        for (Element jd : position.select("jobDescriptions > jobDescription")) {
            String value = childText(jd, "value");
            if (value == null) {
                continue;
            }
            String section = childText(jd, "name");
            if (section != null) {
                sb.append(section).append(":\n");
            }
            sb.append(value).append("\n\n");
        }
        return sb.isEmpty() ? null : sb.toString().trim();
    }

    private String childText(Element parent, String tag) {
        Element el = parent.selectFirst("> " + tag);
        if (el == null) {
            return null;
        }
        String text = el.text().trim();
        return text.isEmpty() ? null : text;
    }
}
