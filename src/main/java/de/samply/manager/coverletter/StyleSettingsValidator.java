package de.samply.manager.coverletter;

import de.samply.manager.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Guards the style form against settings that would silently produce an unusable
 * letter - zones outside the sheet, an address field that ends above where it
 * starts, a font size no reader would accept. The style settings arrive from the
 * browser, so they are treated as untrusted input like any other request body.
 */
@Component
@RequiredArgsConstructor
public class StyleSettingsValidator {

    /**
     * The font stack is the only style setting that reaches the stylesheet as text
     * rather than as a generated length, so it is restricted to what a CSS font
     * stack legitimately needs. Without this, a crafted value could close the
     * {@code <style>} element and inject markup into a document that a
     * server-side Chromium then loads.
     */
    private static final java.util.regex.Pattern FONT_FAMILY = java.util.regex.Pattern.compile("[A-Za-z0-9 ,-]{1,120}");

    private static final double MIN_FONT_SIZE_PT = 8.0;
    private static final double MAX_FONT_SIZE_PT = 16.0;
    private static final double MIN_LINE_HEIGHT = 1.0;
    private static final double MAX_LINE_HEIGHT = 3.0;

    private final MessageSource messageSource;

    /** Fills unset components with the DIN 5008 defaults and rejects impossible geometry. */
    public StyleSettings validated(StyleSettings settings) {
        StyleSettings s = (settings == null ? StyleSettings.din5008FormB() : settings).orDefaults();

        if (s.leftMarginMm() < 0 || s.rightMarginMm() < 0
                || s.leftMarginMm() + s.rightMarginMm() >= StyleSettings.PAGE_WIDTH_MM) {
            throw badRequest("error.coverLetter.style.horizontalMargins", StyleSettings.PAGE_WIDTH_MM);
        }
        if (s.topMarginMm() < 0 || s.bottomMarginMm() < 0
                || s.topMarginMm() + s.bottomMarginMm() >= StyleSettings.PAGE_HEIGHT_MM) {
            throw badRequest("error.coverLetter.style.verticalMargins", StyleSettings.PAGE_HEIGHT_MM);
        }
        if (s.returnAddressTopMm() < s.topMarginMm()) {
            throw badRequest("error.coverLetter.style.returnAddressAboveMargin");
        }
        if (!(s.returnAddressTopMm() < s.recipientTopMm()
                && s.recipientTopMm() < s.addressFieldBottomMm()
                && s.addressFieldBottomMm() <= s.subjectTopMm())) {
            throw badRequest("error.coverLetter.style.zoneOrder");
        }
        if (s.subjectTopMm() > StyleSettings.PAGE_HEIGHT_MM - s.bottomMarginMm()) {
            throw badRequest("error.coverLetter.style.subjectBelowPage");
        }
        if (s.infoBlockTopMm() < s.topMarginMm() || s.infoBlockTopMm() > s.subjectTopMm()) {
            throw badRequest("error.coverLetter.style.infoBlockTop");
        }
        if (s.infoBlockLeftMm() < s.leftMarginMm()
                || s.infoBlockLeftMm() >= StyleSettings.PAGE_WIDTH_MM - s.rightMarginMm()) {
            throw badRequest("error.coverLetter.style.infoBlockLeft");
        }
        if (!FONT_FAMILY.matcher(s.fontFamily()).matches()) {
            throw badRequest("error.coverLetter.style.fontFamily");
        }
        if (s.fontSizePt() < MIN_FONT_SIZE_PT || s.fontSizePt() > MAX_FONT_SIZE_PT) {
            throw badRequest("error.coverLetter.style.fontSize", MIN_FONT_SIZE_PT, MAX_FONT_SIZE_PT);
        }
        if (s.lineHeight() < MIN_LINE_HEIGHT || s.lineHeight() > MAX_LINE_HEIGHT) {
            throw badRequest("error.coverLetter.style.lineHeight", MIN_LINE_HEIGHT, MAX_LINE_HEIGHT);
        }
        return s;
    }

    private ApiException.BadRequest badRequest(String key, Object... args) {
        return new ApiException.BadRequest(messageSource.getMessage(key, args, Locale.ROOT));
    }
}
