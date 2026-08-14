package de.samply.manager.types;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LayoutLetterKey {
    DIN5008_COVER_LETTER_A("din5008FormA"),
    DIN5008_COVER_LETTER_B("din5008FormB"),
    DIN5008_CV("din5008-cv");

    private final String templateKey;
}
