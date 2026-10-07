package ctosim;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormLoaderTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class FormLoaderErrorTest {
    @TempDir Path temp;

    @Test void missingPathIsReportedWithItsFullPath() {
        Path missing = temp.resolve("not-present.xml");
        FormLoadException error = assertThrows(FormLoadException.class, () -> FormLoader.load(missing));
        assertSourceAndExplanation(error, missing.toString());
    }

    @Test void directoryCannotBeReadAsAFormFile() throws Exception {
        Path directory = Files.createDirectory(temp.resolve("directory.xml"));
        FormLoadException error = assertThrows(FormLoadException.class, () -> FormLoader.load(directory));
        assertSourceAndExplanation(error, directory.toString());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void emptyInputIsAlwaysAFormLoadException(boolean fromPath) throws Exception {
        String source = "empty.xml";
        Path file = Files.write(temp.resolve(source), new byte[0]);
        FormLoadException error = assertThrows(FormLoadException.class, () -> {
            if (fromPath) FormLoader.load(file);
            else FormLoader.load(new ByteArrayInputStream(new byte[0]), source);
        });
        assertSourceAndExplanation(error, source);
    }

    @ParameterizedTest
    @ValueSource(strings = {"malformed.xml", "not-xform.xml", "no-model.xml", "missing-id.xml", "bad-xpath.xml"})
    void invalidStreamIsRejectedAtLoadTimeWithItsSourceAndReason(String name) throws Exception {
        String source = "supplied source — " + name;
        try (var in = new FormLoaderLoadingTest.TrackedStream(fixture("invalid/" + name), 11)) {
            FormLoadException error = assertThrows(FormLoadException.class, () -> FormLoader.load(in, source));
            assertSourceAndExplanation(error, source);
            assertFalse(in.closed, "Even a rejected stream remains owned by the caller");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"malformed.xml", "not-xform.xml", "no-model.xml", "missing-id.xml", "bad-xpath.xml"})
    void invalidFileIsRejectedAtLoadTimeWithItsSourceAndReason(String name) throws Exception {
        Path file = Files.write(temp.resolve(name), fixture("invalid/" + name));
        FormLoadException error = assertThrows(FormLoadException.class, () -> FormLoader.load(file));
        assertSourceAndExplanation(error, name);
    }

    @ParameterizedTest @ValueSource(ints = {0, 24})
    void ioFailuresAreWrappedAndTheOriginalFailureIsRetained(int readableBytes) throws Exception {
        IOException original = new IOException("Synthetic read failure for testing");
        byte[] bytes = fixture("minimal.xml");
        class FailingStream extends InputStream {
            int position;
            boolean closed;
            @Override public int read() throws IOException {
                if (position >= readableBytes) throw original;
                return bytes[position++] & 0xff;
            }
            @Override public void close() { closed = true; }
        }
        try (FailingStream in = new FailingStream()) {
            FormLoadException error = assertThrows(FormLoadException.class, () -> FormLoader.load(in, "broken-stream.xml"));
            assertSourceAndExplanation(error, "broken-stream.xml");
            assertNotNull(error.getCause(), "Underlying exceptions must be retained");
            Throwable cause = error.getCause();
            for (int i = 0; i < 20 && cause != original && cause != null; i++) cause = cause.getCause();
            assertSame(original, cause, "The cause chain must retain the actual IOException");
            assertFalse(in.closed);
        }
    }

    @Test void nullPathThrowsNullPointerException() {
        assertThrows(NullPointerException.class, () -> FormLoader.load((Path) null));
    }

    @Test void nullStreamThrowsNullPointerException() {
        assertThrows(NullPointerException.class, () -> FormLoader.load((InputStream) null, "null-stream.xml"));
    }

    @Test void nullSourceNameThrowsNullPointerException() throws Exception {
        byte[] bytes = fixture("minimal.xml");
        assertThrows(NullPointerException.class, () -> FormLoader.load(new ByteArrayInputStream(bytes), null));
    }

    @Test void twoNullStreamArgumentsThrowNullPointerException() {
        assertThrows(NullPointerException.class, () -> FormLoader.load((InputStream) null, null));
    }

    @ParameterizedTest @ValueSource(strings = {" \t\r\n ", "This is not XML.", "\uFEFF"})
    void whitespacePlainTextAndBomOnlyInputAreRejected(String input) {
        FormLoadException error = assertThrows(FormLoadException.class, () -> loadXml(input, "no-form.xml"));
        assertSourceAndExplanation(error, "no-form.xml");
    }

    @Test void anHtmlLocalNameInTheWrongNamespaceIsNotAnXform() throws Exception {
        String xml = fixtureText("minimal.xml").replace("http://www.w3.org/1999/xhtml", "urn:not-xhtml");
        FormLoadException error = assertThrows(FormLoadException.class, () -> loadXml(xml, "wrong-namespace.xml"));
        assertSourceAndExplanation(error, "wrong-namespace.xml");
    }

    @Test void aFailedLoadDoesNotPoisonALaterValidLoad() throws Exception {
        assertThrows(FormLoadException.class, () -> loadFixture("invalid/bad-xpath.xml"));
        LoadedForm valid = loadFixture("minimal.xml");
        assertEquals("minimal", valid.formId());
        assertEquals("initial", value(valid.newEntryController(), "answer"));
        assertTrue(valid.stubbedFunctions().isEmpty());
    }

    @Test void exceptionConstructorsPreserveMessageAndCause() {
        IOException cause = new IOException("original");
        FormLoadException simple = new FormLoadException("plain explanation");
        assertEquals("plain explanation", simple.getMessage());
        assertNull(simple.getCause());
        FormLoadException wrapped = new FormLoadException("source.xml: cannot read input", cause);
        assertEquals("source.xml: cannot read input", wrapped.getMessage());
        assertSame(cause, wrapped.getCause());
    }

    private static void assertSourceAndExplanation(FormLoadException error, String source) {
        String message = error.getMessage();
        assertNotNull(message, "A load failure needs a diagnostic message");
        assertTrue(message.contains(source), () -> "Diagnostic does not name source '" + source + "': " + message);
        String explanation = message.replace(source, "").replaceAll("[\\p{Punct}\\s]", "");
        assertTrue(explanation.codePoints().filter(Character::isLetter).count() >= 3,
                () -> "Diagnostic must contain an explanation beyond the source name: " + message);
    }
}
