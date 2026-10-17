package ctosim;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.SortedSet;
import org.javarosa.core.model.FormDef;
import org.javarosa.core.model.CoreModelModule;
import org.javarosa.core.model.instance.InstanceInitializationFactory;
import org.javarosa.core.services.PrototypeManager;
import org.javarosa.core.util.externalizable.DeserializationException;
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
    private final Attachments attachments;
    private final SortedSet<String> missingAttachments;
    private final Map<String, String> durationCalculations;

    LoadedForm(byte[] xml, String sourceName, String formId, String version, String title,
               SortedSet<String> stubbedFunctions, Attachments attachments, SortedSet<String> missingAttachments,
               Map<String, String> durationCalculations) {
        this.xml = xml.clone();
        this.sourceName = sourceName;
        this.formId = formId;
        this.version = version;
        this.title = title;
        this.stubbedFunctions = stubbedFunctions;
        this.attachments = attachments;
        this.missingAttachments = missingAttachments;
        this.durationCalculations = durationCalculations;
    }

    private Map<String, String> calculations;
    private Map<String, String> constraints;

    /** The engine form's XML (spec 12 reads what the form reads from its attached files). */
    byte[] engineXml() {
        return xml.clone();
    }

    /** Every calculate expression, by bind nodeset (engine form; spec 07, Amendment 9). */
    synchronized Map<String, String> calculations() {
        if (calculations == null) {
            calculations = bindExpressions("calculate");
        }
        return calculations;
    }

    /**
     * Every constraint as written in the engine form, by bind nodeset (spec 07, Amendment 8): a
     * definition read back from its serialised copy no longer holds the constraints' text.
     */
    synchronized Map<String, String> constraints() {
        if (constraints == null) {
            constraints = bindExpressions("constraint");
        }
        return constraints;
    }

    private Map<String, String> bindExpressions(String attribute) {
        try {
            return FormLoader.bindExpressions(xml, sourceName, attribute);
        } catch (FormLoadException e) {
            throw new IllegalStateException(e); // the form was read once already
        }
    }

    /** The calculate expressions that call duration(), by bind nodeset (engine form). */
    Map<String, String> durationCalculations() {
        return durationCalculations;
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
     * The literal pulldata() sources whose CSV file is not in the media directory. See
     * specs/03-surveycto-functions.md, Amendment 3.
     */
    /** The form's attached files. */
    Attachments attachments() {
        return attachments;
    }

    public SortedSet<String> missingAttachments() {
        return missingAttachments;
    }

    /**
     * Opens a session whose clock-dependent values come from {@code clock} and whose random
     * values come from {@code seed}. See specs/06-simulated-sessions.md.
     */
    public FormEntryController newSimulatedSession(SimulatedClock clock, long seed) {
        return newSimulatedSession(clock, seed, Map.of("username", SimulatedProperties.USERNAME));
    }

    /**
     * Opens a simulated session whose metadata properties (deviceid, username, ...) come from
     * {@code properties}. See specs/06-simulated-sessions.md, Amendment 2.
     */
    public FormEntryController newSimulatedSession(SimulatedClock clock, long seed,
                                                   Map<String, String> properties) {
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(properties, "properties");
        Map<String, String> copy = Map.copyOf(properties); // rejects null keys and values
        FormDef def = parse();
        SessionEnvironment.registerSimulated(def, clock, seed, copy);
        return start(def);
    }

    /**
     * Opens a session over a fresh, initialised instance of the form. The definition is
     * reparsed from the loaded bytes so that no state is shared between sessions.
     */
    public FormEntryController newEntryController() {
        return start(parse());
    }

    /**
     * Opens and discards a session, to check at load time that the form initialises. Attached
     * files are not read: pulldata() reads them on the first call of a real session.
     */
    void checkInitialises() {
        try {
            start(FormLoader.parseWithEngine(xml, sourceName, Attachments.NONE));
        } catch (FormLoadException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    /** The engine's definition, parsed once and kept serialised; empty when it cannot be serialised. */
    private byte[] definition;

    /**
     * A fresh definition of the form. Parsing a large form takes most of a submission's time, so
     * the first parse is serialised and later sessions read their own copy from it, as ODK Collect
     * does with its form cache.
     */
    private FormDef parse() {
        byte[] bytes;
        synchronized (this) {
            if (definition == null) {
                FormDef def = parseXml();
                definition = serialise(def);
                return def;
            }
            bytes = definition;
        }
        if (bytes.length == 0) {
            return parseXml();
        }
        try {
            FormDef def = new FormDef();
            def.readExternal(new DataInputStream(new ByteArrayInputStream(bytes)), PrototypeManager.getDefault());
            SurveyCtoFunctions.register(def, attachments);
            return def;
        } catch (IOException | DeserializationException e) {
            throw new IllegalStateException(sourceName + ": the form definition could not be read back", e);
        }
    }

    private FormDef parseXml() {
        try {
            return FormLoader.parseWithEngine(xml, sourceName, attachments);
        } catch (FormLoadException e) {
            // The same bytes parsed successfully when the form was loaded.
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private static boolean prototypesRegistered;

    /** The definition serialised, or an empty array when the engine cannot serialise it. */
    private static byte[] serialise(FormDef def) {
        synchronized (LoadedForm.class) {
            if (!prototypesRegistered) {
                // the classes JavaRosa's JavaRosaCoreModule, CoreModelModule and XFormsModule register
                PrototypeManager.registerPrototypes(new String[] {
                    "org.javarosa.core.services.locale.ResourceFileDataSource",
                    "org.javarosa.core.services.locale.TableLocaleSource"});
                PrototypeManager.registerPrototypes(CoreModelModule.classNames);
                PrototypeManager.registerPrototypes(new String[] {
                    "org.javarosa.model.xform.XPathReference", "org.javarosa.xpath.XPathConditional"});
                PrototypeManager.registerPrototypes(org.javarosa.xpath.XPathParseTool.xpathClasses);
                prototypesRegistered = true;
            }
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            def.writeExternal(new DataOutputStream(out));
            return out.toByteArray();
        } catch (IOException | RuntimeException e) {
            return new byte[0];
        }
    }

    private static FormEntryController start(FormDef def) {
        def.initialize(true, new InstanceInitializationFactory());
        return new FormEntryController(new FormEntryModel(def));
    }
}
