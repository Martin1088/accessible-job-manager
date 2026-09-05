package de.samply.manager.types;

/**
 * Whether a position has been through the review queue yet.
 *
 * <p>Three values and no transitions worth modelling: a position is waiting to
 * be looked at, has been taken over, or has been discarded. A discarded
 * position is filtered out rather than deleted - it keeps the posting from
 * coming back through an import a second time, and if that ever needs cleaning
 * up it is a delete job, not a data model.
 */
public enum TriageState {
    NEW,
    ACCEPTED,
    DISMISSED
}
