package de.samply.manager.dto;

/**
 * What is left in the queue after accepting or dismissing one position.
 *
 * <p>The count is part of the answer rather than something the caller fetches
 * afterwards: the frontend announces it ("Accepted. 7 positions remaining.")
 * in the same turn it removes the row, and a second request would make that
 * announcement race the removal.
 */
public record TriageResultDto(long remaining) {
}
