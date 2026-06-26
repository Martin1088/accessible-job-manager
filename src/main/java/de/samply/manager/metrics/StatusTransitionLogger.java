package de.samply.manager.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;

public class StatusTransitionLogger {
    private static final Logger log = LoggerFactory.getLogger(StatusTransitionLogger.class);

    @EventListener
    public void onStatusTransition(StatusTransitionEvent event) {
        log.info("Application {} status: {} -> {}",
                event.applicationId(), event.from(), event.to());
    }
}
