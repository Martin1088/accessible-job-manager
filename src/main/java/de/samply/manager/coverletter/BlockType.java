package de.samply.manager.coverletter;

/**
 * The kinds of content block the letter editor can produce. Deliberately small:
 * every block maps to one semantic HTML element in the DIN 5008 template, so the
 * frontend never has to know how a block is laid out.
 */
public enum BlockType {
    PARAGRAPH,
    HEADING,
    BULLET_LIST
}
