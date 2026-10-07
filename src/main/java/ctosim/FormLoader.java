package ctosim;

import java.io.InputStream;
import java.nio.file.Path;

/** Reads a SurveyCTO form definition (XForm). See specs/01-form-loader.md. */
public final class FormLoader {

    private FormLoader() {
    }

    public static LoadedForm load(Path xform) throws FormLoadException {
        throw new UnsupportedOperationException("not implemented");
    }

    public static LoadedForm load(InputStream in, String sourceName) throws FormLoadException {
        throw new UnsupportedOperationException("not implemented");
    }
}
