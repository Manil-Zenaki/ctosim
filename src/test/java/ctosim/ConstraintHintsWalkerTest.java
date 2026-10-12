package ctosim;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static ctosim.ConstraintHintsTestSupport.attribute;
import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class ConstraintHintsWalkerTest {
    private static String constrained(String name, String type, String expression) {
        return bind(name, type, "required=\"true()\" constraint=\"" + attribute(expression) + "\"");
    }

    @Test void walkerUsesLiteralBoundsLengthsAndPatternsOutsideDefaults() throws Exception {
        var form = form("<integer/><decimal/><phone/><word/><date/><stamp/>",
                constrained("integer", "int", ". >= 900000000 and . <= 999999999")
                + constrained("decimal", "decimal", ". >= 2000 and . <= 2001")
                + constrained("phone", "string", "regex(., '^09[0-9]{8}$')")
                + constrained("word", "string", "string-length(.) = 2")
                + constrained("date", "date", ". = date('2050-02-28')")
                + constrained("stamp", "dateTime", ". >= date('2050-03-01') and . < date('2050-03-02')"),
                input("integer") + input("decimal") + input("phone") + input("word") + input("date") + input("stamp"));
        var walker = new FormWalker(form, completeConfig());
        for (int n = 1; n <= 10; n++) {
            Submission s = walker.walk(n);
            assertEquals(6, s.answered());
            assertTrue(s.warnings().isEmpty(), s.warnings().toString());
            long integer = Long.parseLong(value(s, "integer"));
            assertTrue(integer >= 900000000 && integer <= 999999999);
            double decimal = Double.parseDouble(value(s, "decimal"));
            assertTrue(decimal >= 2000 && decimal <= 2001);
            assertTrue(value(s, "phone").matches("^09[0-9]{8}$"));
            assertTrue(value(s, "word").matches("[a-z]{2}"));
            assertEquals("2050-02-28", value(s, "date"));
            assertTrue(value(s, "stamp").startsWith("2050-03-01T"));
        }
    }

    @Test void hintsAreComputedAfterEarlierAnswersAndCalculations() throws Exception {
        var loaded = form("<base/><calculated/><group><relative/><absolute/></group>",
                bind("base", "int", "") + bind("calculated", "int", "calculate=\"/data/base + 100\"")
                + constrained("group/relative", "int", ". = ../../calculated + 1")
                + constrained("group/absolute", "int", ". = /data/base + 2"),
                input("base") + "<group ref=\"/data/group\"><label>Group</label>"
                        + input("group/relative") + input("group/absolute") + "</group>");
        var config = config("\"missingRate\":0,\"fields\":{\"base\":{\"min\":5000,\"max\":9000}}");
        var walker = new FormWalker(loaded, config);
        for (int n = 1; n <= 20; n++) {
            Submission s = walker.walk(n);
            int base = Integer.parseInt(value(s, "base"));
            assertEquals(Integer.toString(base + 101), value(s, "group/relative"));
            assertEquals(Integer.toString(base + 2), value(s, "group/absolute"));
            assertTrue(s.warnings().isEmpty(), s.warnings().toString());
            assertEquals(3, s.answered());
        }
        assertSameSubmission(walker.walk(3), new FormWalker(loaded, config).walk(3));
    }

    @Test void eachRepeatUsesItsOwnCurrentSiblingValue() throws Exception {
        var loaded = form("<size>3</size><row jr:template=\"\"><base/><answer/></row>",
                bind("size", "int", "") + bind("row/base", "int", "calculate=\"index() * 1000\"")
                + constrained("row/answer", "int", ". = ../base"),
                "<repeat nodeset=\"/data/row\" jr:count=\"/data/size\">" + input("row/answer") + "</repeat>");
        Submission s = new FormWalker(loaded, completeConfig()).walk(1);
        assertEquals(3, count(s, "row"));
        for (int i = 1; i <= 3; i++) {
            assertEquals(Integer.toString(i * 1000), value(s, "row[" + i + "]/answer"));
        }
        assertEquals(3, s.answered());
        assertTrue(s.warnings().isEmpty(), s.warnings().toString());
    }

    @Test void dateFunctionHintUsesSimulatedInterviewDate() throws Exception {
        var loaded = single("date", "required=\"true()\" constraint=\". = today()\"");
        var walker = new FormWalker(loaded, config("\"start\":\"2051-05-01T08:00:00\",\"fieldworkDays\":10"));
        for (int n = 1; n <= 10; n++) {
            Submission s = walker.walk(n);
            assertEquals(s.start().toLocalDate().toString(), value(s, "answer"));
            assertTrue(s.warnings().isEmpty(), s.warnings().toString());
        }
    }

    @Test void ignoredConstraintShapesStillUseEngineRetryFallback() throws Exception {
        var loaded = single("int", "required=\"true()\" constraint=\". * 2 = 2\"");
        var walker = new FormWalker(loaded, config("\"integerRange\":{\"min\":0,\"max\":1}"));
        for (int n = 1; n <= 20; n++) {
            Submission s = walker.walk(n);
            assertEquals("1", value(s, "answer"));
            assertTrue(s.warnings().isEmpty(), s.warnings().toString());
        }
    }

    @Test @Timeout(20)
    void impossibleConstraintStillGivesConstraintAndRequiredWarnings() throws Exception {
        var loaded = single("int", "required=\"true()\" constraint=\"" + attribute(". > 10 and . < 0") + "\"");
        Submission s = new FormWalker(loaded, config("\"fields\":{\"answer\":{\"value\":\"5\"}}")).walk(1);
        assertEquals("", value(s, "answer"));
        assertEquals(0, s.answered());
        assertWarning(s, "answer", "constraint");
        assertWarning(s, "answer", "required");
    }

    @Test @Timeout(20)
    void candidatesRescueFixedValueThatContradictsTheConstraintWithoutWarnings() throws Exception {
        var loaded = single("int", "required=\"true()\" constraint=\""
                + attribute(". >= 900000000 and . <= 999999999") + "\"");
        Submission s = new FormWalker(loaded, config("\"fields\":{\"answer\":{\"value\":\"5\"}}")).walk(1);
        assertEquals("900000000", value(s, "answer"));
        assertEquals(1, s.answered());
        assertTrue(s.warnings().isEmpty(), s.warnings().toString());
    }

    @Test @Timeout(600)
    void publishedMigrationPhoneQuestionsNeverProduceWarningsInSubmissionsOneThroughFifty() throws Exception {
        Path path = Path.of("samples/xforms/ethiopia_migration_household.xml");
        assertTrue(Files.isRegularFile(path), "Published acceptance form must be present");
        var walker = new FormWalker(FormLoader.load(path), config("""
                "fields":{"availability_04":{"value":"1"},"consent":{"value":"1"},"hh_one_exist":{"value":"1"}}
                """));
        for (int n = 1; n <= 50; n++) {
            Submission s = walker.walk(n);
            for (String warning : s.warnings()) {
                assertFalse(warning.contains("contact_cell") || warning.contains("contact_others_phone_out"), warning);
            }
        }
    }

    @Test @Timeout(600)
    void convertedCoveragePhonesMatchForAllFiftyConsentingSubmissions() throws Exception {
        Path path = Path.of("samples/xforms/ctosim_coverage.xml");
        assertTrue(Files.isRegularFile(path), "XForm converted from samples/coverage/ctosim_coverage.xlsx must be present");
        var walker = new FormWalker(FormLoader.load(path), config("\"missingRate\":0,\"fields\":{\"consent\":{\"value\":\"1\"}}"));
        for (int n = 1; n <= 50; n++) {
            Submission s = walker.walk(n);
            var nodes = xml(s).getElementsByTagNameNS("*", "phone");
            assertTrue(nodes.getLength() > 0, "Acceptance must exercise phone in submission " + n);
            for (int i = 0; i < nodes.getLength(); i++) {
                String phone = nodes.item(i).getTextContent();
                assertTrue(phone.matches("^09[0-9]{8}$"), "submission " + n + ": " + phone);
            }
        }
    }
}
