package de.samply.manager.coverletter;

import de.samply.manager.exception.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StyleSettingsValidatorTest {

    private final StyleSettingsValidator validator =
            new StyleSettingsValidator(CoverLetterFixtures.messageSource());

    private static StyleSettings onlyFontSize(double fontSizePt) {
        return new StyleSettings(null, null, null, null, null, null, null, null, null, null,
                null, fontSizePt, null, null);
    }

    @Test
    void fillsUnsetComponentsWithDin5008Defaults() {
        StyleSettings validated = validator.validated(onlyFontSize(12.0));

        assertThat(validated.fontSizePt()).isEqualTo(12.0);
        assertThat(validated.leftMarginMm()).isEqualTo(24.1);
        assertThat(validated.recipientTopMm()).isEqualTo(62.7);
        assertThat(validated.subjectTopMm()).isEqualTo(98.46);
        assertThat(validated.foldMarks()).isTrue();
    }

    @Test
    void nullSettingsFallBackToDin5008FormB() {
        assertThat(validator.validated(null)).isEqualTo(StyleSettings.din5008FormB());
    }

    @Test
    void rejectsMarginsWiderThanTheSheet() {
        StyleSettings tooWide = StyleSettings.din5008FormB().orDefaults();
        assertThatThrownBy(() -> validator.validated(withLeftMargin(tooWide, 190.0)))
                .isInstanceOf(ApiException.BadRequest.class)
                .hasMessageContaining("210");
    }

    @Test
    void rejectsZonesThatDoNotRunTopToBottom() {
        StyleSettings defaults = StyleSettings.din5008FormB();
        StyleSettings recipientAboveReturnAddress = new StyleSettings(
                defaults.leftMarginMm(), defaults.rightMarginMm(), defaults.topMarginMm(), defaults.bottomMarginMm(),
                62.7, 45.0, defaults.addressFieldBottomMm(),
                defaults.infoBlockTopMm(), defaults.infoBlockLeftMm(), defaults.subjectTopMm(),
                defaults.fontFamily(), defaults.fontSizePt(), defaults.lineHeight(), defaults.foldMarks());

        assertThatThrownBy(() -> validator.validated(recipientAboveReturnAddress))
                .isInstanceOf(ApiException.BadRequest.class);
    }

    @Test
    void rejectsAFontStackThatCouldBreakOutOfTheStylesheet() {
        StyleSettings injected = new StyleSettings(null, null, null, null, null, null, null, null, null, null,
                "Arial</style><script>alert(1)</script>", null, null, null);

        assertThatThrownBy(() -> validator.validated(injected))
                .isInstanceOf(ApiException.BadRequest.class);
    }

    @Test
    void rejectsUnreadableFontSizes() {
        assertThatThrownBy(() -> validator.validated(onlyFontSize(4.0)))
                .isInstanceOf(ApiException.BadRequest.class)
                .hasMessageContaining("8");
    }

    private StyleSettings withLeftMargin(StyleSettings settings, double leftMarginMm) {
        return new StyleSettings(leftMarginMm, settings.rightMarginMm(), settings.topMarginMm(),
                settings.bottomMarginMm(), settings.returnAddressTopMm(), settings.recipientTopMm(),
                settings.addressFieldBottomMm(), settings.infoBlockTopMm(), settings.infoBlockLeftMm(),
                settings.subjectTopMm(), settings.fontFamily(), settings.fontSizePt(),
                settings.lineHeight(), settings.foldMarks());
    }
}
