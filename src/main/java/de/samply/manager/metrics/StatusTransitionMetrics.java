package de.samply.manager.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.event.EventListener;

public class StatusTransitionMetrics {
    private final MeterRegistry registry;

    public StatusTransitionMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @EventListener
    public void onStatusTransition(StatusTransitionEvent event) {
        registry.counter("jobmanager.status.transitions",
                "from", event.from(),
                "to", event.to()).increment();
    }
}
