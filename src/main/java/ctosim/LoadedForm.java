package ctosim;

import java.util.SortedSet;
import org.javarosa.core.model.FormDef;
import org.javarosa.core.model.instance.InstanceInitializationFactory;
import org.javarosa.form.api.FormEntryController;
import org.javarosa.form.api.FormEntryModel;

/** A parsed form from which entry sessions are opened. See specs/01-form-loader.md. */
public final class LoadedForm {

    private final byte[] xml;
    private final String sourceName;
    private final String formId;
    private final String version;
    private final String title;
    private final SortedSet<String> stubbedFunctions;

    LoadedForm(byte[] xml, String sourceName, String formId, String version, String title,
               SortedSet<String> stubbedFunctions) {
        this.xml = xml.clone();
        this.sourceName = sourceName;
        this.formId = formId;
        this.version = version;
        this.title = title;
        this.stubbedFunctions = stubbedFunctions;
    }

    public String sourceName() {
        return sourceName;
    }

    public String formId() {
        return formId;
    }

    /** The form version, or {@code null} when absent. */
    public String version() {
        return version;
    }

    public String title() {
        return title;
    }

    public SortedSet<String> stubbedFunctions() {
        return stubbedFunctions;
    }

    /**
     * Opens a session over a fresh, initialised instance of the form. The definition is
     * reparsed from the loaded bytes so that no state is shared between sessions.
     */
    public FormEntryController newEntryController() {
        FormDef def;
        try {
            def = FormLoader.parseWithEngine(xml, sourceName);
        } catch (FormLoadException e) {
            // The same bytes parsed successfully when the form was loaded.
            throw new IllegalStateException(e.getMessage(), e);
        }
        def.initialize(true, new InstanceInitializationFactory());
        return new FormEntryController(new FormEntryModel(def));
    }
}
