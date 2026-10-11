package ctosim;

import static ctosim.FormWalkerTestSupport.*;

/** Black-box fixtures for spec 07 Amendment 2; no production internals. */
final class FormWalkerAmendment2Support {
    static final String REPORTS = "<violations/><violated/>";
    static final String REPORT_BINDS = bind("violations", "int", "ct:speed-violations=\"count\"")
            + bind("violated", "string", "ct:speed-violations=\"list\"");

    private FormWalkerAmendment2Support() { }

    static LoadedForm amended2(String instance, String binds, String body) throws Exception {
        return loadXml(resource("amendment2/template.xml").replace("__INSTANCE__", instance)
                .replace("__BINDS__", binds).replace("__BODY__", body));
    }

    static LoadedForm timed(String attributes) throws Exception {
        return amended2("<answer/>" + REPORTS,
                bind("answer", "string", attributes) + REPORT_BINDS, input("answer"));
    }

    static SimulationConfig settings(double rate) throws ConfigException {
        return config("\"seed\":937451,\"missingRate\":0,\"specialRate\":0,\"speedViolationRate\":" + rate);
    }

    static LoadedForm deviceForm(boolean question) throws Exception {
        return amended2("<username/><deviceid/><answer/>",
                bind("username", "string", "jr:preload=\"property\" jr:preloadParams=\"username\"")
                + bind("deviceid", "string", "jr:preload=\"property\" jr:preloadParams=\"deviceid\"")
                + bind("answer", "string", "ct:minimum-seconds=\"100\""), question ? input("answer") : "");
    }
}
