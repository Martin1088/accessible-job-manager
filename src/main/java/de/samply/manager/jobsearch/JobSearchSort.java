package de.samply.manager.jobsearch;

/** Result ordering a source supports. The wire value is what Adzuna expects. */
public enum JobSearchSort {

    RELEVANCE("relevance"),
    DATE("date"),
    SALARY("salary"),
    HYBRID("hybrid");

    private final String wireValue;

    JobSearchSort(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }
}
