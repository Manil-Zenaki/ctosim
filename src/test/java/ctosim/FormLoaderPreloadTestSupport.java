package ctosim;

import java.util.Date;
import org.javarosa.form.api.FormEntryController;

import static ctosim.FormLoaderTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

final class FormLoaderPreloadTestSupport {
    static final String ALL_PRELOADS = "metadata-preloads/all-preloads.xml";

    private FormLoaderPreloadTestSupport() {}

    static String singlePreloadXml(String preload, String params, String type) throws Exception {
        return fixtureText("metadata-preloads/single-template.xml")
                .replace("__PRELOAD__", attribute(preload))
                .replace("__PARAMS__", params == null ? "" : "jr:preloadParams=\"" + attribute(params) + "\"")
                .replace("__TYPE__", attribute(type));
    }

    static LoadedForm singlePreload(String preload, String params, String type) throws Exception {
        return loadXml(singlePreloadXml(preload, params, type), "single-preload.xml");
    }

    static void assertCurrentTimestamp(FormEntryController session, String field, long before, long after) {
        Date timestamp = assertInstanceOf(Date.class, value(session, field), "Timestamp must be a date/time value");
        // Allow native second precision without assuming an exact instant or time zone.
        assertTrue(timestamp.getTime() >= before - 1000 && timestamp.getTime() <= after + 1000,
                () -> field + " must reflect the current time: " + timestamp.getTime()
                        + " outside [" + before + ", " + after + "] (with 1s precision tolerance)");
    }

    static String identifier(FormEntryController session, String field) {
        String identifier = assertInstanceOf(String.class, value(session, field));
        assertFalse(identifier.isBlank(), "A native uid must be a nonempty identifier");
        return identifier;
    }
}
