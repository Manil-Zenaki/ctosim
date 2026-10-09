package ctosim;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Writes submissions as SurveyCTO wide and long exports. See specs/08-surveycto-exporter.md. */
public final class SurveyCtoExporter {

    public SurveyCtoExporter(FieldCatalog catalog, LoadedForm form, String serverName) {
        throw new UnsupportedOperationException("not implemented");
    }

    public String wideFileName() {
        throw new UnsupportedOperationException("not implemented");
    }

    public String wide(List<Submission> submissions) {
        throw new UnsupportedOperationException("not implemented");
    }

    public Map<String, String> longFiles(List<Submission> submissions) {
        throw new UnsupportedOperationException("not implemented");
    }

    public void writeAll(List<Submission> submissions, Path directory) throws IOException {
        throw new UnsupportedOperationException("not implemented");
    }
}
