package ctosim;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.condition.IFunctionHandler;

/**
 * Stubs for XPath functions that SurveyCTO provides but the ODK engine does not.
 * Every stub accepts any number of arguments of any type and returns a fixed value.
 */
final class SurveyCtoFunctions {

    /** Return value of every function that has neither a native implementation nor a named stub. */
    static final String FALLBACK_VALUE = "";

    private static final Map<String, Object> NAMED_STUBS = Map.of(
            "pulldata", "",
            "plug-in-metadata", "",
            "username", SimulatedProperties.USERNAME,
            "duration", 0.0,
            "phone-call-log", "",
            "phone-call-duration", 0.0);

    /**
     * Functions implemented inside JavaRosa's XPath evaluator (XPathFuncExpr), plus the
     * namespaced ones that the XForm parser resolves itself. Handlers that the parser
     * registers on a form's evaluation context are detected at load time and added to these.
     */
    static final Set<String> ENGINE_BUILT_INS = Set.of(
            "abs", "acos", "area", "asin", "atan", "atan2", "base64-decode", "boolean",
            "boolean-from-string", "checklist", "coalesce", "concat", "contains", "cos", "count",
            "count-non-empty", "count-selected", "date", "date-time", "decimal-date-time",
            "decimal-time", "depend", "digest", "distance", "enclosed-area", "ends-with", "exp",
            "exp10", "extract-signed", "false", "format-date", "format-date-time", "geofence", "if",
            "indexed-repeat", "instance", "int", "is-selected", "join", "log", "log10", "max", "min",
            "normalize-space", "not", "now", "number", "once", "pi", "position", "pow", "property",
            "random", "randomize", "regex", "round", "selected", "selected-at", "sin", "sqrt",
            "starts-with", "string", "string-length", "substr", "substring-after",
            "substring-before", "sum", "tan", "today", "translate", "true", "uuid", "version",
            "weighted-checklist", "current", "jr:choice-name", "jr:itext");

    private SurveyCtoFunctions() {
    }

    static boolean isNamedStub(String name) {
        return NAMED_STUBS.containsKey(name);
    }

    /** Makes the named stubs and the catch-all fallback evaluable in the given context. */
    static void register(EvaluationContext context) {
        NAMED_STUBS.forEach((name, value) -> context.addFunctionHandler(new ConstantFunction(name, value)));
        context.addFallbackFunctionHandler((name, args, ec) -> FALLBACK_VALUE);
    }

    private static final class ConstantFunction implements IFunctionHandler {
        private final String name;
        private final Object value;

        ConstantFunction(String name, Object value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public List<Class<?>[]> getPrototypes() {
            return Collections.emptyList();
        }

        @Override
        public boolean rawArgs() {
            return true;
        }

        @Override
        public Object eval(Object[] args, EvaluationContext ec) {
            return value;
        }
    }
}
