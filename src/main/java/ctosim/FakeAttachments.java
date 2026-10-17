package ctosim;

import java.util.List;
import java.util.SortedMap;

/** Fictitious attached files built from what the form reads from them. See specs/12-fake-attachments.md. */
public final class FakeAttachments {

    private FakeAttachments() {
    }

    /** The fictitious files for the form's missing attachments, drawn with {@code seed}. */
    public static FakeAttachments of(LoadedForm form, long seed) {
        throw new UnsupportedOperationException("not implemented yet");
    }

    /** Each fictitious file's name (without .csv) and its CSV content. */
    public SortedMap<String, String> files() {
        throw new UnsupportedOperationException("not implemented yet");
    }

    /** The values each key question should be answered with, so that the form finds its rows. */
    public SortedMap<String, List<String>> keyAnswers() {
        throw new UnsupportedOperationException("not implemented yet");
    }
}
