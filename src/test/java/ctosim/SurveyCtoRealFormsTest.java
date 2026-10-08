package ctosim;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.javarosa.core.model.FormIndex;
import org.javarosa.core.model.data.IntegerData;
import org.javarosa.core.model.data.SelectOneData;
import org.javarosa.core.model.data.helper.Selection;
import org.javarosa.form.api.FormEntryController;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SurveyCtoTestSupport.*;
import static org.javarosa.form.api.FormEntryController.*;
import static org.junit.jupiter.api.Assertions.*;

class SurveyCtoRealFormsTest {
    static Stream<Path> publishedForms() throws Exception {
        try (Stream<Path> files = Files.list(Path.of("samples", "xforms"))) {
            List<Path> forms = files.filter(p -> p.getFileName().toString().endsWith(".xml")).sorted().toList();
            assertFalse(forms.isEmpty(), "The real-form acceptance suite must not silently run zero cases");
            return forms.stream();
        }
    }

    @ParameterizedTest(name = "{0} loads and opens initialized sessions") @MethodSource("publishedForms")
    void everyPublishedXformLoadsAndInitializes(Path path) throws Exception {
        LoadedForm loaded = FormLoader.load(path);
        for (int i = 0; i < 2; i++) {
            FormEntryController session = loaded.newEntryController();
            assertEquals(EVENT_BEGINNING_OF_FORM, session.getModel().getEvent());
            assertNotNull(session.getModel().getForm().getMainInstance().getRoot());
        }
    }

    @ParameterizedTest(name = "Household roster with {0} engine-created instances") @ValueSource(ints = {1, 3, 25})
    void realHouseholdRosterCalculatesStringInstanceNumbers(int count) throws Exception {
        LoadedForm loaded = FormLoader.load(Path.of("samples", "xforms", "ethiopia_migration_household.xml"));
        var session = loaded.newEntryController();
        var form = session.getModel().getForm();
        // Set answers through the engine so that relevance and dependency processing run.
        // N=1 is covered even though the published parent group is relevant only for N>1.
        form.setValue(new SelectOneData(new Selection("1")), child(root(session), "availability_04").getRef(), true);
        form.setValue(new SelectOneData(new Selection("1")), child(root(session), "introduce_05").getRef(), true);
        form.setValue(new IntegerData(count), child(root(session), "hh_count_06").getRef(), true);
        var container = child(root(session), "member_interviewed");
        int existing = container.getChildMultiplicity("hhroster");
        while (existing < count) {
            FormIndex index = findRosterIndex(session);
            // -1 is JavaRosa's new-instance position, also used by descendIntoNewRepeat().
            // This does not navigate through relevance-filtered new-repeat prompts.
            form.createNewRepeat(form.descendIntoRepeat(index, -1));
            assertEquals(existing + 1, container.getChildMultiplicity("hhroster"), "Engine creates one instance");
            existing++;
        }
        for (int k = 0; k < count; k++) {
            Object number = value(child(container.getChild("hhroster", k), "instancenumber"));
            assertValue(Integer.toString(k + 1), number);
        }
    }

    private static FormIndex findRosterIndex(FormEntryController session) {
        var model = session.getModel();
        FormIndex index = FormIndex.createBeginningOfFormIndex();
        // Enumerate the model structure rather than navigating only relevant questions.
        for (int i = 0; i < 20000; i++) {
            index = model.incrementIndex(index);
            if (index.isEndOfFormIndex()) break;
            var ref = model.getForm().getChildInstanceRef(index);
            if (ref != null && ref.size() == 3 && "hhroster".equals(ref.getName(2))) return index;
        }
        throw new AssertionError("Published form does not expose its household roster repeat");
    }
}
