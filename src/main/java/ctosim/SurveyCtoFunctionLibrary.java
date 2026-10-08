package ctosim;

import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import org.javarosa.core.model.Constants;
import org.javarosa.core.model.FormDef;
import org.javarosa.core.model.ItemsetBinding;
import org.javarosa.core.model.QuestionDef;
import org.javarosa.core.model.SelectChoice;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.condition.IFunctionHandler;
import org.javarosa.core.model.instance.AbstractTreeElement;
import org.javarosa.core.model.instance.FormInstance;
import org.javarosa.core.model.instance.TreeElement;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.xpath.XPathNodeset;
import org.javarosa.xpath.XPathUnhandledException;
import org.javarosa.xpath.expr.XPathFuncExpr;

/**
 * SurveyCTO functions with their SurveyCTO meaning (spec 03). Every handler receives its
 * arguments raw, so field arguments arrive as node sets and the handler can tell which
 * field was passed, not only its value.
 */
final class SurveyCtoFunctionLibrary {

    static final double EARTH_RADIUS_M = 6_371_000;
    /** SurveyCTO's result for a rank that cannot be computed. */
    static final double NO_RANK = 999;

    private static final String UNIT_SEPARATOR = "\u001F";

    private SurveyCtoFunctionLibrary() {
    }

    static List<IFunctionHandler> handlers(FormDef form) {
        return List.of(
                fn("index", 0, 0, (a, ec) -> index(ec)),
                fn(IndexCalls.INTERNAL_NAME, 1, 1, (a, ec) -> index(ec)),
                fn("choice-label", 2, 2, (a, ec) -> choiceLabel(form, a)),
                fn("relevant", 1, 1, (a, ec) -> relevant(a[0], ec)),
                fn("empty", 1, 1, (a, ec) -> str(a[0]).isEmpty()),
                fn("rank-value", 2, 2, (a, ec) -> rankValue(a[0], a[1])),
                fn("rank-index", 2, 2, (a, ec) -> rankIndex(a[0], a[1])),
                fn("count-items", 2, 2, (a, ec) -> (double) items(a[0], a[1]).size()),
                fn("item-at", 3, 3, (a, ec) -> itemAt(items(a[0], a[1]), num(a[2]))),
                fn("item-index", 3, 3, (a, ec) -> (double) items(a[0], a[1]).indexOf(str(a[2]))),
                fn("item-present", 3, 3, (a, ec) -> items(a[0], a[1]).contains(str(a[2]))),
                fn("de-duplicate", 2, 2, (a, ec) -> String.join(str(a[0]), new LinkedHashSet<>(items(a[0], a[1])))),
                fn("linebreak", 0, 0, (a, ec) -> "\n"),
                fn("lower", 1, 1, (a, ec) -> str(a[0]).toLowerCase(Locale.ROOT)),
                fn("upper", 1, 1, (a, ec) -> str(a[0]).toUpperCase(Locale.ROOT)),
                fn("format-number", 1, 1, (a, ec) -> formatNumber(num(a[0]))),
                fn("hash", 1, Integer.MAX_VALUE, (a, ec) -> hash(a)),
                fn("distance-between", 2, 2, (a, ec) -> distance(str(a[0]), str(a[1]))),
                fn("short-geopoint", 1, 1, (a, ec) -> shortGeopoint(str(a[0]))),
                fn("geo-scatter", 2, 2, (a, ec) -> str(a[0])),
                fn("enumerator-name", 0, 0, (a, ec) -> "simulated"),
                fn("enumerator-id", 0, 0, (a, ec) -> "simulated"),
                fn("device-info", 0, 0, (a, ec) -> "ctosim"),
                fn("collect-is-phone-app", 0, 0, (a, ec) -> Boolean.FALSE));
    }

    // --- Position in repeats ------------------------------------------------------------

    /**
     * Position of the innermost repeat instance on the context node's path. Repeat instances
     * do not carry the repeatable flag themselves (only the template does), so each level of
     * the path is checked against the instance's repeat templates.
     */
    private static double index(EvaluationContext ec) {
        TreeReference context = ec.getContextRef();
        if (context == null || !(ec.getMainInstance() instanceof FormInstance instance)) {
            return 1;
        }
        for (int level = context.size() - 1; level >= 0; level--) {
            int multiplicity = context.getMultiplicity(level);
            if (multiplicity >= 0 && instance.getTemplate(context.getSubReference(level)) != null) {
                return multiplicity + 1;
            }
        }
        return 1;
    }

    // --- Choices and fields -------------------------------------------------------------

    /** Same lookup as the engine's jr:choice-name, but from the field's own node reference. */
    private static String choiceLabel(FormDef form, Object[] a) {
        TreeReference field = singleRef(a[0]);
        String value = str(a[1]);
        if (field == null || value.isEmpty()) {
            return "";
        }
        QuestionDef question = FormDef.findQuestionByRef(field, form);
        if (question == null || (question.getControlType() != Constants.CONTROL_SELECT_ONE
                && question.getControlType() != Constants.CONTROL_SELECT_MULTI)) {
            return "";
        }
        ItemsetBinding itemset = question.getDynamicChoices();
        List<SelectChoice> choices = itemset != null ? itemset.getChoices(form, field) : question.getChoices();
        if (choices == null) {
            return "";
        }
        for (SelectChoice choice : choices) {
            if (value.equals(choice.getValue())) {
                String label = choice.getTextID() != null
                        ? form.getLocalizer().getText(choice.getTextID())
                        : choice.getLabelInnerText();
                return label == null ? "" : form.fillTemplateString(label, field);
            }
        }
        return "";
    }

    private static boolean relevant(Object arg, EvaluationContext ec) {
        TreeReference field = singleRef(arg);
        AbstractTreeElement node = field == null ? null : ec.resolveReference(field);
        return node instanceof TreeElement element && element.isRelevant();
    }

    private static TreeReference singleRef(Object arg) {
        return arg instanceof XPathNodeset nodes && nodes.size() > 0 ? nodes.getRefAt(0) : null;
    }

    // --- Ranking ------------------------------------------------------------------------

    private static double rankValue(Object value, Object list) {
        double v = num(value);
        if (Double.isNaN(v)) {
            return NO_RANK;
        }
        int greater = 0;
        for (String item : str(list).trim().split("\\s+")) {
            double x = item.isEmpty() ? Double.NaN : XPathFuncExpr.toNumeric(item);
            if (!Double.isNaN(x) && x > v) {
                greater++;
            }
        }
        return 1 + greater;
    }

    private static double rankIndex(Object position, Object repeated) {
        List<Double> values = new ArrayList<>();
        if (repeated instanceof XPathNodeset nodes) {
            for (int i = 0; i < nodes.size(); i++) {
                values.add(num(nodes.getValAt(i)));
            }
        } else {
            values.add(num(repeated));
        }
        double p = num(position);
        if (Double.isNaN(p) || p != Math.floor(p) || p < 1 || p > values.size()) {
            return NO_RANK;
        }
        int at = (int) p - 1;
        double v = values.get(at);
        if (Double.isNaN(v)) {
            return NO_RANK;
        }
        int ahead = 0;
        for (int i = 0; i < values.size(); i++) {
            double x = values.get(i);
            if (!Double.isNaN(x) && (x > v || (x == v && i < at))) {
                ahead++;
            }
        }
        return 1 + ahead;
    }

    // --- Lists of items -----------------------------------------------------------------

    private static List<String> items(Object sepArg, Object listArg) {
        String sep = str(sepArg);
        String list = str(listArg);
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        if (sep.isEmpty()) {
            return List.of(list);
        }
        List<String> items = new ArrayList<>();
        int from = 0;
        for (int at = list.indexOf(sep); at >= 0; at = list.indexOf(sep, from)) {
            items.add(list.substring(from, at));
            from = at + sep.length();
        }
        items.add(list.substring(from));
        return items;
    }

    private static String itemAt(List<String> items, double n) {
        if (Double.isNaN(n) || n != Math.floor(n) || n < 0 || n >= items.size()) {
            return "";
        }
        return items.get((int) n);
    }

    // --- Strings ------------------------------------------------------------------------

    private static String formatNumber(double n) {
        if (Double.isNaN(n) || Double.isInfinite(n)) {
            return "";
        }
        DecimalFormat format = new DecimalFormat("#,##0.###", DecimalFormatSymbols.getInstance(Locale.US));
        format.setRoundingMode(RoundingMode.HALF_EVEN);
        String s = format.format(n);
        return s.equals("-0") ? "0" : s;
    }

    private static String hash(Object[] args) {
        List<String> parts = new ArrayList<>(args.length);
        for (Object arg : args) {
            parts.add(str(arg));
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(String.join(UNIT_SEPARATOR, parts).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    // --- Geography ----------------------------------------------------------------------

    private static double distance(String p1, String p2) {
        double[] a = latLon(p1);
        double[] b = latLon(p2);
        if (a == null || b == null) {
            return Double.NaN;
        }
        double lat1 = Math.toRadians(a[0]);
        double lat2 = Math.toRadians(b[0]);
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(b[1] - a[1]);
        double h = Math.pow(Math.sin(dLat / 2), 2) + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(dLon / 2), 2);
        h = Math.min(1, Math.max(0, h)); // rounding can push antipodal points just past 1
        return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(h));
    }

    private static double[] latLon(String point) {
        String[] items = point.trim().split("\\s+");
        if (items.length < 2) {
            return null;
        }
        double lat = XPathFuncExpr.toNumeric(items[0]);
        double lon = XPathFuncExpr.toNumeric(items[1]);
        if (Double.isNaN(lat) || Double.isNaN(lon) || lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            return null;
        }
        return new double[] {lat, lon};
    }

    private static String shortGeopoint(String point) {
        String[] items = point.trim().split("\\s+");
        return items.length < 2 || items[0].isEmpty() ? "" : items[0] + " " + items[1];
    }

    // --- Argument conversion ------------------------------------------------------------

    private static String str(Object arg) {
        return XPathFuncExpr.toString(XPathFuncExpr.unpack(arg));
    }

    private static double num(Object arg) {
        return XPathFuncExpr.toNumeric(XPathFuncExpr.unpack(arg));
    }

    // --- Handler plumbing ---------------------------------------------------------------

    @FunctionalInterface
    private interface Body {
        Object eval(Object[] args, EvaluationContext ec);
    }

    private static IFunctionHandler fn(String name, int minArgs, int maxArgs, Body body) {
        return new IFunctionHandler() {
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
                if (args.length < minArgs || args.length > maxArgs) {
                    throw new XPathUnhandledException(name + "() does not accept " + args.length + " argument(s)");
                }
                return body.eval(args, ec);
            }
        };
    }
}
