package ctosim;

/** Kinds of question the generator can answer. See specs/05-answer-generator.md. */
public enum QuestionKind {
    TEXT, INTEGER, DECIMAL, DATE, TIME, DATETIME,
    SELECT_ONE, SELECT_MULTIPLE,
    GEOPOINT, GEOTRACE, GEOSHAPE,
    BARCODE, IMAGE, AUDIO, VIDEO, FILE,
    ACKNOWLEDGE
}
