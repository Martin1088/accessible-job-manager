package de.samply.manager.metrics;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "job-manager.metrics")
public class MetricsProperties {
    private boolean enabled = true;
    private boolean logStatusTransitions = false;

    private boolean countStatusTransitions = false;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isLogStatusTransitions() { return logStatusTransitions; }
    public void setLogStatusTransitions(boolean v) { this.logStatusTransitions = v; }

    public boolean isCountStatusTransitions() { return countStatusTransitions; }
    public void setCountStatusTransitions(boolean v) { this.countStatusTransitions = v; }
}
