package ctosim;

import java.util.Arrays;
import java.util.stream.Collectors;

import static ctosim.FormWalkerAmendment7Support.quote;
import static ctosim.FormWalkerTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Black-box helpers for spec 07 Amendment 11; no implementation oracle. */
final class FormWalkerAmendment11Support {
    private FormWalkerAmendment11Support() { }

    static LoadedForm fixture11(String name) throws Exception {
        return loadXml(resource("amendment11/" + name + ".xml"));
    }

    static SimulationConfig settings11(String members) throws Exception {
        return config("\"seed\":110731,\"missingRate\":0,\"specialRate\":0"
                + (members.isEmpty() ? "" : "," + members));
    }

    static String consent11(double rate, String... fields) {
        return consent11(rate, "1", "0", fields);
    }

    static String consent11(double rate, String yes, String no, String[] fields) {
        return "\"consent\":{\"fields\":["
                + Arrays.stream(fields).map(FormWalkerAmendment7Support::quote).collect(Collectors.joining(","))
                + "],\"rate\":" + rate + ",\"yes\":" + quote(yes) + ",\"no\":" + quote(no) + "}";
    }

    static String field11(String members) {
        return "\"fields\":{\"answer\":{" + members + "}}";
    }

    static String escape11(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;");
    }

    static String choices11(String path, String... codes) {
        StringBuilder body = new StringBuilder("<select1 ref=\"/data/" + path + "\"><label>Consent</label>");
        for (String code : codes) body.append("<item><label>Code ").append(escape11(code))
                .append("</label><value>").append(escape11(code)).append("</value></item>");
        return body.append("</select1>").toString();
    }

    static LoadedForm choice11(String attributes, String... codes) throws Exception {
        return form("<answer/>", bind("answer", "string", attributes), choices11("answer", codes));
    }

    static void clean11(Submission submission) {
        assertTrue(submission.warnings().isEmpty(), submission.warnings().toString());
        assertTrue(submission.unsatisfiedConstraints().isEmpty());
        assertTrue(submission.formErrors().isEmpty());
    }

    static void consentWarning11(Submission submission, String field, String code) {
        assertTrue(submission.warnings().stream().anyMatch(w -> w.startsWith("submission "
                + submission.number() + ":") && w.contains(field) && w.contains(code)),
                () -> "Expected exact field and attempted consent code in warning: " + submission.warnings());
    }

    static void same11(Submission expected, Submission actual) {
        assertSameSubmission(expected, actual);
        assertEquals(expected.submitted(), actual.submitted());
        assertEquals(expected.unsatisfiedConstraints(), actual.unsatisfiedConstraints());
        assertEquals(expected.formErrors(), actual.formErrors());
    }
}
