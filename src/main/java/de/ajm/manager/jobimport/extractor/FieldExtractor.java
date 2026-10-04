package de.ajm.manager.jobimport.extractor;

public interface FieldExtractor {

    ExtractionResult extract(ExtractionContext ctx);

    ConfidenceTier tier();
}