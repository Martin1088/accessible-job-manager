package de.samply.manager.types;

/**
 * How an applicant is meant to send their application for a position.
 *
 * <p>{@link #UNKNOWN} is a real answer, not a failure: plenty of postings
 * never say. Recording it distinguishes "we looked and the posting is silent"
 * from "nobody has checked yet", which is the difference between the user
 * needing to find out and the user needing to do nothing.
 */
public enum ApplicationMethod {

    /** The posting asks for the application by email. */
    EMAIL,

    /** The posting points to an application form or an apply button/link. */
    WEB_FORM,

    /** The posting does not say which way to apply. */
    UNKNOWN
}
