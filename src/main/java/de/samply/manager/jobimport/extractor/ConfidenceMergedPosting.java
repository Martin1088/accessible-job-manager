package de.samply.manager.jobimport.extractor;

import de.samply.manager.dto.CompanyDto;
import de.samply.manager.dto.CompanyLocationDto;
import de.samply.manager.dto.CompanyPositionDto;
import de.samply.manager.types.Gender;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects the ExtractionResults of the tiers (JSON-LD -> ATS-API -> ... -> LLM)
 * and merges them PER FIELD. No first-wins over the whole object: each field
 * keeps its own confidence and is only replaced by a candidate from a
 * stronger tier.
 *
 * Usage in the pipeline:
 *   var merged = new ConfidenceMergedPosting();
 *   for (FieldExtractor e : extractors) {
 *       merged.mergeLowerPriority(e.extract(ctx));
 *       if (merged.isComplete()) break;   // don't even call the LLM tier
 *   }
 *   JobPosting jp = merged.toJobPosting();
 */
public final class ConfidenceMergedPosting {

    private FieldValue<String> title           = FieldValue.absent();
    private FieldValue<String> companyName     = FieldValue.absent();
    private FieldValue<Gender>  contactGender  = FieldValue.absent();
    private FieldValue<String> contactLastName = FieldValue.absent();
    private FieldValue<String> contactEmail    = FieldValue.absent();
    private FieldValue<String> postedAt        = FieldValue.absent();
    private FieldValue<String> deadline        = FieldValue.absent();
    private FieldValue<String> employmentType  = FieldValue.absent();
    private FieldValue<String> sourceJobId     = FieldValue.absent();
    private FieldValue<String> rawDescription  = FieldValue.absent();

    // Locations deduplicated by (city, street), keeping the strongest tier each time.
    private final Map<String, ExtractionResult.LocationValue> locations = new LinkedHashMap<>();

    /**
     * Merges in one tier's result. Per field, the rule is: only set if the
     * existing field is empty OR the candidate comes from a better tier.
     */
    public void mergeLowerPriority(ExtractionResult r) {
        title           = pick(title, r.title());
        companyName     = pick(companyName, r.companyName());
        contactGender   = pick(contactGender, r.contactGender());
        contactLastName = pick(contactLastName, r.contactLastName());
        contactEmail    = pick(contactEmail, r.contactEmail());
        postedAt        = pick(postedAt, r.postedAt());
        deadline        = pick(deadline, r.deadline());
        employmentType  = pick(employmentType, r.employmentType());
        sourceJobId     = pick(sourceJobId, r.sourceJobId());
        rawDescription  = pick(rawDescription, r.rawDescription());

        for (ExtractionResult.LocationValue loc : r.locations()) {
            mergeLocation(loc);
        }
    }

    /**
     * Core of the per-field logic:
     *   - candidate empty          -> keep what's there
     *   - current field empty      -> take the candidate
     *   - both set                 -> only replace if current is NOT outranking
     *
     * "outranks(candidateTier)" is true when current is equally good or
     * better -> then current stays. Otherwise the stronger candidate wins.
     */
    private <T> FieldValue<T> pick(FieldValue<T> current, FieldValue<T> candidate) {
        if (!candidate.present()) {
            return current;
        }
        if (!current.present()) {
            return candidate;
        }
        return current.outranks(candidate.tier()) ? current : candidate;
    }

    private void mergeLocation(ExtractionResult.LocationValue candidate) {
        String key = normalizeKey(candidate);
        ExtractionResult.LocationValue existing = locations.get(key);
        if (existing == null) {
            locations.put(key, candidate);
            return;
        }
        String city   = betterString(existing.city(),   existing.tier(),
                                      candidate.city(),  candidate.tier());
        String street = betterString(existing.street(), existing.tier(),
                                      candidate.street(),candidate.tier());
        ConfidenceTier tier = existing.tier().priority() <= candidate.tier().priority()
                ? existing.tier() : candidate.tier();
        locations.put(key, new ExtractionResult.LocationValue(city, street, tier));
    }

    private String betterString(String a, ConfidenceTier ta, String b, ConfidenceTier tb) {
        boolean aSet = a != null && !a.isBlank();
        boolean bSet = b != null && !b.isBlank();
        if (aSet && bSet) return ta.priority() <= tb.priority() ? a : b;
        return aSet ? a : (bSet ? b : null);
    }

    private String normalizeKey(ExtractionResult.LocationValue l) {
        String c = l.city() == null ? "" : l.city().toLowerCase().strip();
        return c;
    }

    /**
     * Required fields for a usable import. Once all of these are present,
     * the pipeline can stop before the expensive LLM tier runs.
     * Contact is deliberately NOT required - it's structurally often missing.
     */
    public boolean isComplete() {
        return title.present()
            && companyName.present()
            && !locations.isEmpty();
    }

    public JobPosting toJobPosting() {
        List<CompanyLocationDto> locs = new ArrayList<>();
        for (ExtractionResult.LocationValue l : locations.values()) {
            CompanyLocationDto loc = new CompanyLocationDto();
            loc.setStreet(l.street());
            loc.setCity(l.city());
            locs.add(loc);
        }

        CompanyPositionDto position = new CompanyPositionDto();
        position.setTitle(title.value());
        position.setContactGender(contactGender.value());
        position.setContactLastName(contactLastName.value());
        position.setEmail(contactEmail.value());
        CompanyDto company = new CompanyDto();
        company.setName(companyName.value());
        company.setLocations(locs);
        company.setPositions(List.of(position));

        return new JobPosting(
                company,
                sourceJobId.asOptional().orElse(null),
                postedAt.asOptional().orElse(null),
                deadline.asOptional().orElse(null),
                employmentType.asOptional().orElse(null),
                buildProvenance()
        );
    }

    /**
     * Provenance: per field, which tier the value came from. Used by the UI
     * ("please review" for HEURISTIC/LLM) and for fixture-based accuracy
     * measurement.
     */
    private Map<String, ConfidenceTier> buildProvenance() {
        Map<String, ConfidenceTier> p = new LinkedHashMap<>();
        putIf(p, "title", title);
        putIf(p, "companyName", companyName);
        putIf(p, "contactGender", contactGender);
        putIf(p, "contactLastName", contactLastName);
        putIf(p, "contactEmail", contactEmail);
        putIf(p, "postedAt", postedAt);
        putIf(p, "deadline", deadline);
        putIf(p, "employmentType", employmentType);
        putIf(p, "sourceJobId", sourceJobId);
        return p;
    }

    private void putIf(Map<String, ConfidenceTier> p, String key, FieldValue<?> fv) {
        if (fv.present()) p.put(key, fv.tier());
    }
}
