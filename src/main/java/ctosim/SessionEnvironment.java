package ctosim;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.function.Supplier;
import org.javarosa.core.model.FormDef;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.condition.IFunctionHandler;
import org.javarosa.core.model.data.DateTimeData;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.StringData;
import org.javarosa.core.model.instance.TreeElement;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.model.utils.DateUtils;
import org.javarosa.core.model.utils.IPreloadHandler;
import org.javarosa.core.util.MathUtils;
import org.javarosa.core.util.PropertyUtils;

/**
 * Where a session takes the time and randomness that the form's expressions and metadata
 * preloads use (spec 06). The form's own today(), now(), random() and uuid() calls are
 * rewritten to internal functions by {@link EngineExpressions}; this class gives those
 * functions their meaning: native in ordinary sessions, simulated in simulated sessions.
 */
final class SessionEnvironment {

    static final String TODAY = "ctosim-today";
    static final String NOW = "ctosim-now";
    static final String RANDOM = "ctosim-random";
    static final String UUID = "ctosim-uuid";

    private SessionEnvironment() {
    }

    /** Native behaviour, for ordinary sessions: the real clock and the engine's randomness. */
    static void registerNative(FormDef form) {
        EvaluationContext ec = form.getEvaluationContext();
        ec.addFunctionHandler(function(TODAY, () -> DateUtils.roundDate(new Date())));
        ec.addFunctionHandler(function(NOW, Date::new));
        ec.addFunctionHandler(function(RANDOM, () -> MathUtils.getRand().nextDouble()));
        ec.addFunctionHandler(function(UUID, PropertyUtils::genUUID));
    }

    /**
     * Simulated behaviour: time from {@code clock}, randomness from {@code seed}, device
     * properties (deviceid, username, ...) from {@code properties}.
     */
    static void registerSimulated(FormDef form, SimulatedClock clock, long seed, Map<String, String> properties) {
        Draws draws = new Draws(seed);
        LocalDateTime opened = clock.now();
        EvaluationContext ec = form.getEvaluationContext();
        ec.addFunctionHandler(function(TODAY, () -> toDate(clock.now().toLocalDate().atStartOfDay())));
        ec.addFunctionHandler(function(NOW, () -> toDate(clock.now())));
        ec.addFunctionHandler(keyed(RANDOM, (args, e) -> draws.next("random", args, e).nextDouble()));
        ec.addFunctionHandler(keyed(UUID, (args, e) -> uuid(draws.next("uuid", args, e))));
        ec.addFunctionHandler(function("duration",
                () -> (double) Math.floorDiv(Duration.between(opened, clock.now()).toNanos(), 1_000_000_000L)));

        ec.addFunctionHandler(function("username", () -> properties.getOrDefault("username", "")));

        form.getPreloader().addPreloadHandler(new TimestampPreload(clock));
        form.getPreloader().addPreloadHandler(new PropertyPreload(properties));
        form.getPreloader().addPreloadHandler(new UidPreload(draws));
    }

    /**
     * The session's random values. The engine does not evaluate expressions in the same order
     * every time a form is parsed, so a single sequential generator would hand the same draws
     * to different fields from one session to the next. Each draw is instead derived from the
     * seed, the call site (the number given to the internal call), the node being evaluated
     * and how many draws that call site already made for that node.
     */
    static final class Draws {
        private final long seed;
        private final java.util.Map<String, Integer> counts = new java.util.HashMap<>();

        Draws(long seed) {
            this.seed = seed;
        }

        SplittableRandom next(String kind, String site, String node) {
            String key = kind + '|' + site + '|' + node;
            int n = counts.merge(key, 1, Integer::sum);
            return new SplittableRandom(hash(seed, key + '|' + n));
        }

        SplittableRandom next(String kind, Object[] args, EvaluationContext ec) {
            String site = args.length > 0 ? org.javarosa.xpath.expr.XPathFuncExpr.toString(
                    org.javarosa.xpath.expr.XPathFuncExpr.unpack(args[0])) : "";
            TreeReference context = ec.getContextRef();
            return next(kind, site, context == null ? "" : context.toString(true));
        }

        /** 64-bit FNV-1a of the UTF-8 key, mixed with the seed. */
        private static long hash(long seed, String key) {
            long h = 0xcbf29ce484222325L ^ seed;
            for (byte b : key.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
                h ^= b & 0xff;
                h *= 0x100000001b3L;
            }
            return h;
        }
    }

    static Date toDate(LocalDateTime local) {
        return Date.from(local.atZone(ZoneId.systemDefault()).toInstant());
    }

    /** A version-4 UUID (variant 10xx) from the session's random sequence. */
    static String uuid(SplittableRandom random) {
        long high = (random.nextLong() & 0xffffffffffff0fffL) | 0x0000000000004000L;
        long low = (random.nextLong() & 0x3fffffffffffffffL) | 0x8000000000000000L;
        return new java.util.UUID(high, low).toString();
    }

    private static IFunctionHandler keyed(String name, java.util.function.BiFunction<Object[], EvaluationContext, Object> body) {
        return new IFunctionHandler() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public List<Class<?>[]> getPrototypes() {
                return List.of();
            }

            @Override
            public boolean rawArgs() {
                return true;
            }

            @Override
            public Object eval(Object[] args, EvaluationContext ec) {
                return body.apply(args, ec);
            }
        };
    }

    private static IFunctionHandler function(String name, Supplier<Object> value) {
        return new IFunctionHandler() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public List<Class<?>[]> getPrototypes() {
                return List.of();
            }

            @Override
            public boolean rawArgs() {
                return true; // the internal calls carry an ignored numbering argument
            }

            @Override
            public Object eval(Object[] args, EvaluationContext ec) {
                return value.get();
            }
        };
    }

    /** start: the clock when the session opens; end: the clock when the instance is post-processed. */
    private static final class TimestampPreload implements IPreloadHandler {
        private final SimulatedClock clock;

        TimestampPreload(SimulatedClock clock) {
            this.clock = clock;
        }

        @Override
        public String preloadHandled() {
            return "timestamp";
        }

        @Override
        public IAnswerData handlePreload(String params) {
            return "start".equals(params) ? new DateTimeData(toDate(clock.now())) : null;
        }

        @Override
        public boolean handlePostProcess(TreeElement node, String params) {
            if ("end".equals(params)) {
                node.setAnswer(new DateTimeData(toDate(clock.now())));
                return true;
            }
            return false;
        }
    }

    /** Metadata fields read from the device (jr:preload="property"): the session's own values. */
    private static final class PropertyPreload implements IPreloadHandler {
        private final Map<String, String> properties;

        PropertyPreload(Map<String, String> properties) {
            this.properties = properties;
        }

        @Override
        public String preloadHandled() {
            return "property";
        }

        @Override
        public IAnswerData handlePreload(String params) {
            String value = properties.get(params);
            return value == null || value.isEmpty() ? null : new StringData(value);
        }

        @Override
        public boolean handlePostProcess(TreeElement node, String params) {
            return false;
        }
    }

    /** instanceID: "uuid:" and an identifier drawn from the session's seed. */
    private static final class UidPreload implements IPreloadHandler {
        private final Draws draws;

        UidPreload(Draws draws) {
            this.draws = draws;
        }

        @Override
        public String preloadHandled() {
            return "uid";
        }

        @Override
        public IAnswerData handlePreload(String params) {
            // The engine does not say which node it is preloading; uid nodes are filled one per
            // instance, so the count kept by Draws tells successive ones apart.
            return new StringData("uuid:" + uuid(draws.next("uid", "preload", String.valueOf(params))));
        }

        @Override
        public boolean handlePostProcess(TreeElement node, String params) {
            return false;
        }
    }
}
