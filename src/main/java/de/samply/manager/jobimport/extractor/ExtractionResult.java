package de.samply.manager.jobimport.extractor;

import de.samply.manager.types.Gender;
import java.util.ArrayList;
import java.util.List;

public final class ExtractionResult {

    private final FieldValue<String> title;
    private final FieldValue<String> companyName;
    private final List<LocationValue> locations;
    private final FieldValue<Gender> contactGender;
    private final FieldValue<String> contactLastName;
    private final FieldValue<String> contactEmail;
    private final FieldValue<String> postedAt;
    private final FieldValue<String> deadline;
    private final FieldValue<String> employmentType;
    private final FieldValue<String> sourceJobId;
    private final FieldValue<String> rawDescription;

    private ExtractionResult(Builder b) {
        this.title           = b.title;
        this.companyName     = b.companyName;
        this.locations       = List.copyOf(b.locations);
        this.contactGender   = b.contactGender;
        this.contactLastName = b.contactLastName;
        this.contactEmail    = b.contactEmail;
        this.postedAt        = b.postedAt;
        this.deadline        = b.deadline;
        this.employmentType  = b.employmentType;
        this.sourceJobId     = b.sourceJobId;
        this.rawDescription  = b.rawDescription;
    }

    public static ExtractionResult empty() {
        return builder(ConfidenceTier.LLM).build(); // defaultTier doesn't matter - all fields empty
    }

    public static Builder builder(ConfidenceTier defaultTier) {
        return new Builder(defaultTier);
    }

    public FieldValue<String> title()            { return title; }
    public FieldValue<String> companyName()      { return companyName; }
    public List<LocationValue> locations()       { return locations; }
    public FieldValue<Gender>  contactGender()   { return contactGender; }
    public FieldValue<String> contactLastName()  { return contactLastName; }
    public FieldValue<String> contactEmail()     { return contactEmail; }
    public FieldValue<String> postedAt()         { return postedAt; }
    public FieldValue<String> deadline()         { return deadline; }
    public FieldValue<String> employmentType()   { return employmentType; }
    public FieldValue<String> sourceJobId()      { return sourceJobId; }
    public FieldValue<String> rawDescription()   { return rawDescription; }

    public static final class Builder {
        private final ConfidenceTier defaultTier;

        private FieldValue<String> title           = FieldValue.absent();
        private FieldValue<String> companyName     = FieldValue.absent();
        private final List<LocationValue> locations = new ArrayList<>();
        private FieldValue<Gender>  contactGender  = FieldValue.absent();
        private FieldValue<String> contactLastName = FieldValue.absent();
        private FieldValue<String> contactEmail    = FieldValue.absent();
        private FieldValue<String> postedAt        = FieldValue.absent();
        private FieldValue<String> deadline        = FieldValue.absent();
        private FieldValue<String> employmentType  = FieldValue.absent();
        private FieldValue<String> sourceJobId     = FieldValue.absent();
        private FieldValue<String> rawDescription  = FieldValue.absent();

        private Builder(ConfidenceTier defaultTier) {
            this.defaultTier = defaultTier;
        }

        public Builder title(String v)           { this.title = wrap(v); return this; }
        public Builder companyName(String v)     { this.companyName = wrap(v); return this; }
        public Builder contactLastName(String v) { this.contactLastName = wrap(v); return this; }
        public Builder contactEmail(String v)    { this.contactEmail = wrap(v); return this; }
        public Builder postedAt(String v)        { this.postedAt = wrap(v); return this; }
        public Builder deadline(String v)        { this.deadline = wrap(v); return this; }
        public Builder employmentType(String v)  { this.employmentType = wrap(v); return this; }
        public Builder sourceJobId(String v)     { this.sourceJobId = wrap(v); return this; }
        public Builder rawDescription(String v)  { this.rawDescription = wrap(v); return this; }
        public Builder contactGender(Gender g) {
            return contactGender(g, defaultTier);
        }
        public Builder contactGender(Gender g, ConfidenceTier tier) {
            if (g != null) this.contactGender = FieldValue.of(g, tier);
            return this;
        }

        public Builder addLocation(String city, String street) {
            boolean hasCity = city != null && !city.isBlank();
            boolean hasStreet = street != null && !street.isBlank();
            if (hasCity || hasStreet) {
                locations.add(new LocationValue(
                        hasCity ? city.trim() : null,
                        hasStreet ? street.trim() : null,
                        defaultTier));
            }
            return this;
        }

        private FieldValue<String> wrap(String v) {
            return (v == null || v.isBlank())
                    ? FieldValue.absent()
                    : FieldValue.of(v.trim(), defaultTier);
        }

        public ExtractionResult build() {
            return new ExtractionResult(this);
        }
    }

    public record LocationValue(String city, String street, ConfidenceTier tier) {}
}
