package de.samply.manager.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnProperty(prefix = "job-manager.metrics", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(MetricsProperties.class)
public class MetricsConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "job-manager.metrics", name = "log-status-transitions")
    public StatusTransitionLogger statusTransitionLogger() {
        return new StatusTransitionLogger();
    }

    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnProperty(prefix = "job-manager.metrics", name = "count-status-transitions")
    public StatusTransitionMetrics statusTransitionMetrics(MeterRegistry registry) {
        return new StatusTransitionMetrics(registry);
    }
}
