package ctosim;

import java.time.LocalDateTime;
import java.util.Map;
import org.javarosa.form.api.FormEntryController;

import static ctosim.SurveyCtoTestSupport.*;

final class SurveyCtoAmendment5Support {
    private SurveyCtoAmendment5Support() {}

    static LoadedForm form(String resultBind, String targetBind, String action, String bodyAction)
            throws Exception {
        return load(fixture("amendment5-selected.xml")
                .replace("__RESULT_BIND__", resultBind)
                .replace("__TARGET_BIND__", targetBind)
                .replace("__ACTION__", action)
                .replace("__BODY_ACTION__", bodyAction));
    }

    static String bracket(String expression) { return "concat('[', " + expression + ", ']')"; }

    static LoadedForm calculation(String expression) throws Exception {
        return form("calculate=\"" + attribute(bracket(expression)) + "\"", "", "", "");
    }

    static String setvalue(String event, String expression) {
        return "<setvalue event=\"" + event + "\" ref=\"/data/result\" value=\""
                + attribute(bracket(expression)) + "\"/>";
    }

    static FormEntryController session(LoadedForm form, String kind) {
        var clock = new SimulatedClock(LocalDateTime.of(2026, 1, 5, 12, 0));
        return switch (kind) {
            case "ordinary" -> form.newEntryController();
            case "simulated" -> form.newSimulatedSession(clock, 54321L);
            case "properties" -> form.newSimulatedSession(clock, 54321L, Map.of("username", "tester"));
            default -> throw new AssertionError(kind);
        };
    }
}
