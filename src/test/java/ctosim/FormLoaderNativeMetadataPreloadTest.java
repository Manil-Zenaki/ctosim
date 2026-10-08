package ctosim;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.javarosa.form.api.FormEntryController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static ctosim.FormLoaderPreloadTestSupport.*;
import static ctosim.FormLoaderTestSupport.*;
import static org.javarosa.form.api.FormEntryController.EVENT_BEGINNING_OF_FORM;
import static org.junit.jupiter.api.Assertions.*;

class FormLoaderNativeMetadataPreloadTest {
    @TempDir Path temp;

    @ParameterizedTest(name = "{0}/{1} opens without caller engine configuration")
    @CsvSource({"timestamp,start,dateTime", "timestamp,end,dateTime", "uid,,string"})
    void eachNativePreloadAloneLoadsAndOpensAtTheBeginning(String preload, String params, String type)
            throws Exception {
        var session = singlePreload(preload, params, type).newEntryController();
        assertEquals(EVENT_BEGINNING_OF_FORM, session.getModel().getEvent());
        assertEquals("initial", value(session, "answer"));
    }

    @ParameterizedTest(name = "path loading supports {0}/{1}")
    @CsvSource({"timestamp,start,dateTime", "timestamp,end,dateTime", "uid,,string"})
    void nativePreloadsAlsoWorkThroughThePathOverload(String preload, String params, String type)
            throws Exception {
        Path source = Files.writeString(temp.resolve("native-preload.xml"),
                singlePreloadXml(preload, params, type), StandardCharsets.UTF_8);
        long before = System.currentTimeMillis();
        var session = FormLoader.load(source).newEntryController();
        long after = System.currentTimeMillis();
        if (preload.equals("uid")) {
            identifier(session, "metadata");
        } else if (params.equals("start")) {
            assertCurrentTimestamp(session, "metadata", before, after);
        } else {
            assertNull(node(session, "metadata").getValue());
        }
    }

    @Test void startTimestampUsesTheCurrentTimeWhenEachSessionIsInitialized() throws Exception {
        LoadedForm form = singlePreload("timestamp", "start", "dateTime");
        for (int i = 0; i < 3; i++) {
            long before = System.currentTimeMillis();
            var session = form.newEntryController();
            long after = System.currentTimeMillis();
            assertCurrentTimestamp(session, "metadata", before, after);
        }
    }

    @Test void endTimestampIsUnsetUntilNativePostProcessing() throws Exception {
        var session = singlePreload("timestamp", "end", "dateTime").newEntryController();
        assertNull(node(session, "metadata").getValue());
    }

    @Test void endTimestampUsesTheCurrentTimeDuringNativePostProcessing() throws Exception {
        var session = singlePreload("timestamp", "end", "dateTime").newEntryController();
        long before = System.currentTimeMillis();
        session.getModel().getForm().postProcessInstance();
        long after = System.currentTimeMillis();
        assertCurrentTimestamp(session, "metadata", before, after);
    }

    @Test void uidWithoutPreloadParamsHasANonemptyStringValueImmediately() throws Exception {
        var session = singlePreload("uid", null, "string").newEntryController();
        identifier(session, "metadata");
    }

    @Test void uidIsGeneratedPerSessionInsteadOfCopyingOneIdentifierFromTheLoadedForm() throws Exception {
        LoadedForm form = singlePreload("uid", null, "string");
        List<FormEntryController> sessions = new ArrayList<>();
        List<String> identifiers = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            var session = form.newEntryController();
            sessions.add(session);
            identifiers.add(identifier(session, "metadata"));
        }
        assertTrue(new HashSet<>(identifiers).size() > 1,
                "Random native identifiers must not be one cached or fixed value for every session");
        for (int i = 0; i < sessions.size(); i++) {
            assertEquals(identifiers.get(i), identifier(sessions.get(i), "metadata"),
                    "Opening another session must not replace an existing session's identifier");
        }
    }
}
