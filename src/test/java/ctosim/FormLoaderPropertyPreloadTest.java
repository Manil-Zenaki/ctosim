package ctosim;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.javarosa.core.model.data.StringData;
import org.javarosa.form.api.FormEntryController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormLoaderPreloadTestSupport.*;
import static ctosim.FormLoaderTestSupport.*;
import static org.javarosa.form.api.FormEntryController.*;
import static org.junit.jupiter.api.Assertions.*;

class FormLoaderPropertyPreloadTest {
    @TempDir Path temp;

    @ParameterizedTest(name = "property {0} opens without caller engine configuration")
    @ValueSource(strings = {"deviceid", "subscriberid", "simserial", "phonenumber", "username"})
    void eachPropertyAloneLoadsAndOpensAnInitializedSession(String property) throws Exception {
        LoadedForm form = singlePreload("property", property, "string");
        FormEntryController session = assertDoesNotThrow(form::newEntryController);
        assertEquals(EVENT_BEGINNING_OF_FORM, session.getModel().getEvent());
        assertEquals("initial", value(session, "answer"));
    }

    @Test void usernameIsTheSpecifiedStringBeforeAnyNavigation() throws Exception {
        var session = singlePreload("property", "username", "string").newEntryController();
        assertEquals("simulated", value(session, "metadata"));
    }

    @ParameterizedTest(name = "property {0} leaves the instance node value null")
    @ValueSource(strings = {"deviceid", "subscriberid", "simserial", "phonenumber"})
    void unavailablePropertiesHaveNullAnswerObjectsRatherThanEmptyStrings(String property) throws Exception {
        var session = singlePreload("property", property, "string").newEntryController();
        assertNull(node(session, "metadata").getValue());
    }

    @ParameterizedTest(name = "path loading supports property {0}")
    @ValueSource(strings = {"deviceid", "subscriberid", "simserial", "phonenumber", "username"})
    void propertyPreloadsAlsoWorkThroughThePathOverload(String property) throws Exception {
        Path source = Files.writeString(temp.resolve(property + ".xml"),
                singlePreloadXml("property", property, "string"), StandardCharsets.UTF_8);
        var session = FormLoader.load(source).newEntryController();
        if (property.equals("username")) {
            assertEquals("simulated", value(session, "metadata"));
        } else {
            assertNull(node(session, "metadata").getValue());
        }
    }

    @ParameterizedTest(name = "property {0} is independent in earlier and later sessions")
    @ValueSource(strings = {"deviceid", "subscriberid", "simserial", "phonenumber", "username"})
    void answeringAMetadataFieldCannotChangeOtherSessionsPreloadedValues(String property) throws Exception {
        LoadedForm form = singlePreload("property", property, "string");
        var first = form.newEntryController();
        var alreadyOpen = form.newEntryController();
        moveToQuestion(first, "metadata");
        assertEquals(ANSWER_OK, first.answerQuestion(new StringData("changed"), true));
        var openedLater = form.newEntryController();
        for (var untouched : new FormEntryController[] {alreadyOpen, openedLater}) {
            if (property.equals("username")) {
                assertEquals("simulated", value(untouched, "metadata"));
            } else {
                assertNull(node(untouched, "metadata").getValue());
            }
        }
        assertEquals("changed", value(first, "metadata"));
    }

    @Test void preloadedUsernameAgreesWithTheUsernameFunctionInEachSession() throws Exception {
        LoadedForm form = loadFixture("metadata-preloads/username-and-function.xml");
        for (int i = 0; i < 3; i++) {
            var session = form.newEntryController();
            assertEquals("simulated", value(session, "operator"));
            assertEquals(value(session, "operator"), value(session, "function_result"));
            assertEquals(value(session, "operator"), evaluate(session, "username()"));
        }
        assertEquals(Set.of("username"), form.stubbedFunctions());
    }

    @Test void preloadAttributesAreRecognizedByNamespaceRatherThanLiteralPrefix() throws Exception {
        String xml = singlePreloadXml("property", "username", "string")
                .replace("xmlns:jr=", "xmlns:metadata=").replace("jr:", "metadata:");
        var session = loadXml(xml, "preload-namespace-alias.xml").newEntryController();
        assertEquals("simulated", value(session, "metadata"));
    }
}
