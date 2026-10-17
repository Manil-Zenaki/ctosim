package ctosim;

import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.xpath.XPathParseTool;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.SurveyCtoAmendment5Support.*;
import static ctosim.SurveyCtoTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SurveyCtoAmendment5ScopeTest {
    @ParameterizedTest @ValueSource(strings = {
            "selected('1 3 4', 3)", "selected('1 3 4', 2)",
            "selected('1 3 4', /data/number)", "selected('2.5', /data/decimal)",
            "selected('1 3 4', /data/blank)", "selected(3, 3)",
            "selected('1 3 4', '3')", "selected('1 3 4', string(/data/number))"})
    void callsParsedOutsideTheFormKeepTheNativeOutcomeInEverySession(String expression) throws Exception {
        // Include a stored numeric selected call without evaluating it at load
        // time, so this independent scope check can run before implementation.
        var loaded = form("", "constraint=\"selected(/data/many, /data/number)\"", "", "");
        for (String kind : new String[]{"ordinary", "simulated", "properties"}) {
            var entry = session(loaded, kind);
            var form = entry.getModel().getForm();
            var parsed = XPathParseTool.parseXPath(expression);
            Object expected;
            try {
                expected = parsed.eval(form.getMainInstance(), new EvaluationContext(form.getMainInstance()));
            } catch (Exception nativeFailure) {
                var actual = assertThrows(Exception.class, () -> evaluate(entry, expression), kind);
                assertEquals(nativeFailure.getClass(), actual.getClass(), kind);
                continue;
            }
            assertValue(expected, evaluate(entry, expression));
        }
    }
}
