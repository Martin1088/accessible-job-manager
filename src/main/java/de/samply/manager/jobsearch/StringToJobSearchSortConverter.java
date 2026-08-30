package de.samply.manager.jobsearch;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Binds {@code ?sortBy=date} as well as {@code ?sortBy=DATE}; Spring's default
 * enum conversion is case-sensitive, and an API that 400s on a lowercase sort
 * value is a needless trap.
 */
@Component
public class StringToJobSearchSortConverter implements Converter<String, JobSearchSort> {

    @Override
    public JobSearchSort convert(String source) {
        return JobSearchSort.valueOf(source.trim().toUpperCase(Locale.ROOT));
    }
}
