package ctosim;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** The field catalog written by the XLSForm converter. See specs/08-surveycto-exporter.md. */
public final class FieldCatalog {

    private FieldCatalog() {
    }

    public static FieldCatalog load(Path json) throws IOException {
        throw new UnsupportedOperationException("not implemented");
    }

    public String formId() {
        throw new UnsupportedOperationException("not implemented");
    }

    public String title() {
        throw new UnsupportedOperationException("not implemented");
    }

    /** The form version, or {@code null} when absent. */
    public String version() {
        throw new UnsupportedOperationException("not implemented");
    }

    /** The fields in catalog order. */
    public List<FieldInfo> fields() {
        throw new UnsupportedOperationException("not implemented");
    }
}
