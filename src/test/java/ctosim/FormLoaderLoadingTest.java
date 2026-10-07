package ctosim;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.TimeZone;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormLoaderTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Temporarily changes JVM default locale and time zone, restoring them in finally")
class FormLoaderLoadingTest {
    @TempDir Path temp;

    @Test void pathSourceNameIsOnlyTheLastPathElement() throws Exception {
        Path dir = Files.createDirectories(temp.resolve("nested/forms"));
        Path file = Files.write(dir.resolve("ménage 東京.xml"), fixture("minimal.xml"));
        LoadedForm form = FormLoader.load(file);
        assertEquals("ménage 東京.xml", form.sourceName());
        assertEquals("minimal", form.formId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"supplied-name.xml", "folder/custom name.xml", "  enquête 東京  ", ""})
    void streamSourceNameIsPreservedExactly(String source) throws Exception {
        assertEquals(source, FormLoader.load(new ByteArrayInputStream(fixture("minimal.xml")), source).sourceName());
    }

    @Test void streamIsConsumedThroughTrailingWhitespaceWithoutBeingClosed() throws Exception {
        byte[] bytes = (fixtureText("minimal.xml") + " \r\n\t".repeat(4096)).getBytes(StandardCharsets.UTF_8);
        try (TrackedStream in = new TrackedStream(bytes, 7)) {
            FormLoader.load(in, "chunked.xml");
            assertEquals(bytes.length, in.position, "The whole stream, including trailing whitespace, must be read");
            assertFalse(in.closed, "The stream belongs to the caller");
            assertEquals(-1, in.read());
        }
    }

    @Test void oneByteReadsDoNotCorruptMultibyteUtf8Characters() throws Exception {
        try (TrackedStream in = new TrackedStream(fixture("metadata.xml"), 1)) {
            LoadedForm form = FormLoader.load(in, "utf8.xml");
            assertEquals("ménage-東京", form.formId());
            assertEquals("naïve — नमस्ते 🌍", value(form.newEntryController(), "answer"));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void leadingUtf8BomIsAcceptedByBothOverloads(boolean fromPath) throws Exception {
        byte[] xml = fixture("metadata.xml");
        byte[] bomXml = new byte[xml.length + 3];
        bomXml[0] = (byte) 0xef;
        bomXml[1] = (byte) 0xbb;
        bomXml[2] = (byte) 0xbf;
        System.arraycopy(xml, 0, bomXml, 3, xml.length);
        LoadedForm form = fromPath
                ? FormLoader.load(Files.write(temp.resolve("bom.xml"), bomXml))
                : FormLoader.load(new ByteArrayInputStream(bomXml), "bom.xml");
        assertEquals("Enquête  café — 東京 & 家庭 🌍", form.title());
        assertEquals("ménage-東京", form.formId());
    }

    @Test void metadataComesFromTheFirstInstanceRootNotOtherIdOrVersionAttributes() throws Exception {
        LoadedForm form = loadFixture("metadata.xml");
        assertEquals("ménage-東京", form.formId());
        assertEquals("0007.02-beta", form.version(), "Versions are strings, not numbers");
    }

    @Test void titleIsDecodedAndTrimmedWithoutCollapsingInternalWhitespace() throws Exception {
        assertEquals("Enquête  café — 東京 & 家庭 🌍", loadFixture("metadata.xml").title());
    }

    @Test void absentVersionIsNull() throws Exception {
        assertNull(loadFixture("minimal.xml").version());
    }

    @Test void aPresentButEmptyVersionIsPreservedAsAnEmptyString() throws Exception {
        String xml = fixtureText("minimal.xml").replace("id=\"minimal\"", "id=\"minimal\" version=\"\"");
        assertEquals("", loadXml(xml, "empty-version.xml").version());
    }

    @Test void absentTitleIsAnEmptyString() throws Exception {
        assertEquals("", loadFixture("no-title.xml").title());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " \t\r\n "})
    void emptyAndWhitespaceOnlyTitlesAreEmpty(String title) throws Exception {
        assertEquals("", loadXml(fixtureText("minimal.xml").replace("Minimal form", title), "title.xml").title());
    }

    @Test void namespacePrefixesDoNotChangeMetadata() throws Exception {
        String xml = fixtureText("metadata.xml").replace("xmlns:h=", "xmlns:html=")
                .replace("<h:", "<html:").replace("</h:", "</html:");
        LoadedForm form = loadXml(xml, "prefix.xml");
        assertEquals("ménage-東京", form.formId());
        assertEquals("Enquête  café — 東京 & 家庭 🌍", form.title());
    }

    @Test void pathAndStreamOverloadsGiveTheSameMetadataAndInitialValues() throws Exception {
        LoadedForm pathForm = FormLoader.load(Files.write(temp.resolve("metadata.xml"), fixture("metadata.xml")));
        LoadedForm streamForm = loadFixture("metadata.xml");
        assertEquals(streamForm.sourceName(), pathForm.sourceName());
        assertEquals(streamForm.formId(), pathForm.formId());
        assertEquals(streamForm.version(), pathForm.version());
        assertEquals(streamForm.title(), pathForm.title());
        assertEquals(streamForm.stubbedFunctions(), pathForm.stubbedFunctions());
        assertEquals(value(streamForm.newEntryController(), "answer"), value(pathForm.newEntryController(), "answer"));
    }

    static Stream<Arguments> environments() {
        return Stream.of(Arguments.of("tr-TR", "Pacific/Kiritimati"),
                Arguments.of("fr-FR", "America/Adak"), Arguments.of("ar-EG", "Asia/Kathmandu"));
    }

    @ParameterizedTest @MethodSource("environments")
    void loadingAndDeterministicCalculationsIgnoreDefaultLocaleAndTimeZone(String language, String zone) throws Exception {
        Locale oldLocale = Locale.getDefault();
        Locale oldDisplay = Locale.getDefault(Locale.Category.DISPLAY);
        Locale oldFormat = Locale.getDefault(Locale.Category.FORMAT);
        TimeZone oldZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag(language));
            TimeZone.setDefault(TimeZone.getTimeZone(zone));
            LoadedForm metadata = loadFixture("metadata.xml");
            assertEquals("ménage-東京", metadata.formId());
            assertEquals("0007.02-beta", metadata.version());
            assertEquals("Enquête  café — 東京 & 家庭 🌍", metadata.title());
            LoadedForm calculated = expressionForm("concat(string(1.5 + 2.25), ':', username())");
            assertEquals("3.75:simulated", value(calculated.newEntryController(), "result"));
            assertEquals(java.util.Set.of("username"), calculated.stubbedFunctions());
        } finally {
            Locale.setDefault(oldLocale);
            Locale.setDefault(Locale.Category.DISPLAY, oldDisplay);
            Locale.setDefault(Locale.Category.FORMAT, oldFormat);
            TimeZone.setDefault(oldZone);
        }
    }

    static final class TrackedStream extends InputStream {
        private final byte[] bytes;
        private final int chunkSize;
        int position;
        boolean closed;

        TrackedStream(byte[] bytes, int chunkSize) { this.bytes = bytes; this.chunkSize = chunkSize; }
        @Override public int read() throws IOException {
            if (closed) throw new IOException("Read after close");
            return position == bytes.length ? -1 : bytes[position++] & 0xff;
        }
        @Override public int read(byte[] target, int offset, int length) throws IOException {
            if (closed) throw new IOException("Read after close");
            if (length == 0) return 0;
            if (position == bytes.length) return -1;
            int count = Math.min(Math.min(length, chunkSize), bytes.length - position);
            System.arraycopy(bytes, position, target, offset, count);
            position += count;
            return count;
        }
        @Override public void close() { closed = true; }
    }
}
