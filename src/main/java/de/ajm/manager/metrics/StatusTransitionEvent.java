package de.ajm.manager.metrics;

public record StatusTransitionEvent(Long applicationId, String from, String to) {
}
