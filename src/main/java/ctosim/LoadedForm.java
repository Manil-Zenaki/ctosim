package ctosim;

import java.util.SortedSet;
import org.javarosa.form.api.FormEntryController;

/** A parsed form from which entry sessions are opened. See specs/01-form-loader.md. */
public final class LoadedForm {

    LoadedForm() {
    }

    public String sourceName() {
        throw new UnsupportedOperationException("not implemented");
    }

    public String formId() {
        throw new UnsupportedOperationException("not implemented");
    }

    /** The form version, or {@code null} when absent. */
    public String version() {
        throw new UnsupportedOperationException("not implemented");
    }

    public String title() {
        throw new UnsupportedOperationException("not implemented");
    }

    public SortedSet<String> stubbedFunctions() {
        throw new UnsupportedOperationException("not implemented");
    }

    public FormEntryController newEntryController() {
        throw new UnsupportedOperationException("not implemented");
    }
}
