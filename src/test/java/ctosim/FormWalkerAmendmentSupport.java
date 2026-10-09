package ctosim;

import java.math.BigDecimal;
import static ctosim.FormWalkerTestSupport.*;

/** Fixtures use only the public contracts in specs 01, 03, 04, 06 and 07. */
final class FormWalkerAmendmentSupport {
    private FormWalkerAmendmentSupport() { }

    static LoadedForm amendedForm(String instance, String binds, String body) throws Exception {
        return loadXml(resource("amendment1/template.xml").replace("__INSTANCE__", instance)
                .replace("__BINDS__", binds).replace("__BODY__", body));
    }

    static String here(String path, String type, String expression, String extra) {
        return bind(path, type, "ct:calculate-here=\"" + expression + "\" " + extra);
    }

    static long integer(Submission submission, String path) throws Exception {
        // Require a whole number, without imposing the serializer's lexical spelling.
        String text = value(submission, path);
        org.junit.jupiter.api.Assertions.assertFalse(text.isBlank(), path + " must contain a whole number");
        return new BigDecimal(text).longValueExact();
    }

    static LoadedForm devices(boolean withQuestions) throws Exception {
        StringBuilder instance = new StringBuilder("<who/><at_here/><question/>");
        StringBuilder binds = new StringBuilder(bind("who", "string", "calculate=\"username()\"")
                + here("at_here", "string", "username()", "readonly=\"true()\"")
                + bind("question", "int", ""));
        for (String name : new String[]{"username", "deviceid", "subscriberid", "simserial", "phonenumber", "custom"}) {
            instance.append('<').append(name).append("/>");
            binds.append(bind(name, "string", "jr:preload=\"property\" jr:preloadParams=\"" + name + "\""));
        }
        return amendedForm(instance.toString(), binds.toString(),
                withQuestions ? input("question") + input("at_here") : "");
    }
}
