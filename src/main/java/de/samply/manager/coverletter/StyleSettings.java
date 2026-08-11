package de.samply.manager.coverletter;

/**
 * Page geometry and typography of the HTML cover letter, in millimetres from the
 * top/left edge of the sheet (points for the font size).
 * <p>
 * The defaults are DIN 5008 Form B ("hoher Briefkopf"): return address at 45 mm,
 * recipient at 62.7 mm, address field ending at 85 mm, subject line at 98.46 mm,
 * a 165 mm wide writing area (24.1 mm left + 20.9 mm right margin), folding marks
 * at 87 mm and 192 mm and the punch mark at 148.5 mm.
 * <p>
 * Every component is boxed so a partial JSON payload from the style form only
 * overrides what it actually sets - {@link #orDefaults()} fills the rest from
 * {@link #din5008FormB()}. The values are consumed exclusively by the server-side
 * template ({@code templates/cover-letter/din5008.html}); no layout measure is
 * ever computed in the frontend.
 */
public record StyleSettings(
        Double leftMarginMm,
        Double rightMarginMm,
        Double topMarginMm,
        Double bottomMarginMm,
        Double returnAddressTopMm,
        Double recipientTopMm,
        Double addressFieldBottomMm,
        Double infoBlockTopMm,
        Double infoBlockLeftMm,
        Double subjectTopMm,
        String fontFamily,
        Double fontSizePt,
        Double lineHeight,
        Boolean foldMarks
) {

    public static final double PAGE_WIDTH_MM = 210.0;
    public static final double PAGE_HEIGHT_MM = 297.0;

    /** Folding marks and punch mark, measured from the top edge of the sheet. */
    public static final double FIRST_FOLD_MARK_MM = 87.0;
    public static final double PUNCH_MARK_MM = 148.5;
    public static final double SECOND_FOLD_MARK_MM = 192.0;

    public static StyleSettings din5008FormB() {
        return new StyleSettings(
                24.1, 20.9, 25.0, 25.0,
                45.0, 62.7, 85.0,
                50.0, 125.0,
                98.46,
                "Arial, Helvetica, sans-serif", 11.0, 1.5, true);
    }

    /** Returns a copy with every unset component filled from {@link #din5008FormB()}. */
    public StyleSettings orDefaults() {
        StyleSettings d = din5008FormB();
        return new StyleSettings(
                or(leftMarginMm, d.leftMarginMm),
                or(rightMarginMm, d.rightMarginMm),
                or(topMarginMm, d.topMarginMm),
                or(bottomMarginMm, d.bottomMarginMm),
                or(returnAddressTopMm, d.returnAddressTopMm),
                or(recipientTopMm, d.recipientTopMm),
                or(addressFieldBottomMm, d.addressFieldBottomMm),
                or(infoBlockTopMm, d.infoBlockTopMm),
                or(infoBlockLeftMm, d.infoBlockLeftMm),
                or(subjectTopMm, d.subjectTopMm),
                fontFamily == null || fontFamily.isBlank() ? d.fontFamily : fontFamily,
                or(fontSizePt, d.fontSizePt),
                or(lineHeight, d.lineHeight),
                foldMarks != null ? foldMarks : d.foldMarks);
    }

    private static Double or(Double value, Double fallback) {
        return value != null ? value : fallback;
    }
}
