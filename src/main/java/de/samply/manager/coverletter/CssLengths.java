package de.samply.manager.coverletter;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates {@link StyleSettings} into ready-to-write CSS lengths for the DIN 5008
 * template.
 * <p>
 * Two things happen here that must not happen in the template:
 * <ul>
 *   <li><b>Locale-free formatting.</b> {@code 24.1} has to reach the stylesheet as
 *       {@code 24.1mm}; a locale-formatted {@code 24,1mm} would be an invalid CSS
 *       length and Chromium would silently drop the declaration.</li>
 *   <li><b>Absolute sheet positions → box offsets.</b> The page margin box already
 *       starts at {@code topMarginMm}/{@code leftMarginMm}, so everything laid out
 *       inside it is expressed relative to that origin. Keeping the arithmetic here
 *       means the template contains no measurements of its own.</li>
 * </ul>
 * Only the first page carries the DIN zones; the flowing letter text simply inherits
 * the {@code @page} margins on every following page.
 */
final class CssLengths {

    private CssLengths() {}

    static Map<String, String> of(StyleSettings settings) {
        StyleSettings s = settings.orDefaults();
        Map<String, String> css = new LinkedHashMap<>();

        css.put("pageWidth", mm(StyleSettings.PAGE_WIDTH_MM));
        css.put("pageHeight", mm(StyleSettings.PAGE_HEIGHT_MM));
        css.put("topMargin", mm(s.topMarginMm()));
        css.put("rightMargin", mm(s.rightMarginMm()));
        css.put("bottomMargin", mm(s.bottomMarginMm()));
        css.put("leftMargin", mm(s.leftMarginMm()));

        // Distance from the top of the writing area down to the return address line,
        // then the two address-field zones stacked below it.
        css.put("returnAddressGap", mm(s.returnAddressTopMm() - s.topMarginMm()));
        css.put("returnAddressHeight", mm(s.recipientTopMm() - s.returnAddressTopMm()));
        css.put("recipientHeight", mm(s.addressFieldBottomMm() - s.recipientTopMm()));
        css.put("addressFieldWidth", mm(85.0));

        css.put("infoBlockTop", mm(s.infoBlockTopMm() - s.topMarginMm()));
        css.put("infoBlockLeft", mm(s.infoBlockLeftMm() - s.leftMarginMm()));
        css.put("infoBlockWidth", mm(StyleSettings.PAGE_WIDTH_MM - s.rightMarginMm() - s.infoBlockLeftMm()));

        css.put("subjectGap", mm(s.subjectTopMm() - s.addressFieldBottomMm()));

        css.put("foldMarkLeft", mm(-s.leftMarginMm()));
        css.put("firstFoldMark", mm(StyleSettings.FIRST_FOLD_MARK_MM - s.topMarginMm()));
        css.put("punchMark", mm(StyleSettings.PUNCH_MARK_MM - s.topMarginMm()));
        css.put("secondFoldMark", mm(StyleSettings.SECOND_FOLD_MARK_MM - s.topMarginMm()));

        css.put("fontFamily", s.fontFamily());
        css.put("fontSize", pt(s.fontSizePt()));
        css.put("smallFontSize", pt(Math.max(7.0, s.fontSizePt() - 3.0)));
        css.put("lineHeight", number(s.lineHeight()));

        return css;
    }

    private static String mm(double value) {
        return number(value) + "mm";
    }

    private static String pt(double value) {
        return number(value) + "pt";
    }

    private static String number(double value) {
        return BigDecimal.valueOf(value)
                .setScale(3, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }
}
