package ctosim;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Date;
import java.util.List;
import org.javarosa.core.model.FormDef;
import org.javarosa.core.model.FormIndex;
import org.javarosa.core.model.condition.Constraint;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.AbstractTreeElement;
import org.javarosa.core.model.instance.TreeElement;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.form.api.FormEntryController;
import org.javarosa.form.api.FormEntryModel;
import org.javarosa.xpath.XPathConditional;
import org.javarosa.xpath.expr.XPathBinaryOpExpr;
import org.javarosa.xpath.expr.XPathBoolExpr;
import org.javarosa.xpath.expr.XPathCmpExpr;
import org.javarosa.xpath.expr.XPathEqExpr;
import org.javarosa.xpath.expr.XPathExpression;
import org.javarosa.xpath.expr.XPathFilterExpr;
import org.javarosa.xpath.expr.XPathFuncExpr;
import org.javarosa.xpath.expr.XPathPathExpr;
import org.javarosa.xpath.expr.XPathStep;
import org.javarosa.xpath.expr.XPathStringLiteral;
import org.javarosa.xpath.expr.XPathUnaryOpExpr;

/**
 * What a question's constraint says about its valid answers. Every component is null when
 * the constraint gives no such hint. See specs/09-constraint-hints.md.
 */
public record ConstraintHints(Bound lower, Bound upper, DateBound earliest, DateBound latest,
                              Integer minLength, Integer maxLength, String pattern,
                              String prefix, Set<String> excluded, Set<String> included,
                              Integer minSelected, Integer maxSelected,
                              List<ConstraintHints> alternatives, List<String> candidates) {

    private static final ConstraintHints NONE = new ConstraintHints(null, null, null, null, null, null, null);

    public ConstraintHints {
        excluded = excluded == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(excluded));
        included = included == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(included));
        alternatives = alternatives == null ? List.of() : List.copyOf(alternatives);
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    /** Hints of spec 09 before Amendment 2: no prefix, choices, alternatives or candidates. */
    public ConstraintHints(Bound lower, Bound upper, DateBound earliest, DateBound latest,
                           Integer minLength, Integer maxLength, String pattern) {
        this(lower, upper, earliest, latest, minLength, maxLength, pattern,
                null, null, null, null, null, null, null);
    }

    public static ConstraintHints none() {
        return NONE;
    }

    /** Hints from the constraint of the question the session is on; none() when there are none. */
    public static ConstraintHints forCurrentQuestion(FormEntryController session) {
        try {
            FormEntryModel model = session.getModel();
            FormIndex index = model.getFormIndex();
            if (index == null || model.getEvent(index) != FormEntryController.EVENT_QUESTION) {
                return NONE;
            }
            FormDef form = model.getForm();
            TreeReference ref = form.getChildInstanceRef(index);
            AbstractTreeElement node = ref == null ? null : form.getMainInstance().resolveReference(ref);
            if (!(node instanceof TreeElement element)) {
                return NONE;
            }
            Constraint constraint = element.getConstraint();
            if (constraint == null || !(constraint.constraint instanceof XPathConditional conditional)) {
                return NONE;
            }
            Alternatives reader = new Alternatives(form, ref);
            List<List<Term>> alternatives = reader.read(conditional.getExpr(), false);
            List<ConstraintHints> sets = new ArrayList<>();
            List<String> candidates = new ArrayList<>();
            for (List<Term> alternative : alternatives) {
                Builder builder = new Builder(form, ref);
                for (Term term : alternative) {
                    builder.term(term.expr(), term.negated());
                }
                sets.add(builder.build());
                builder.addCandidates(candidates);
            }
            List<String> distinct = List.copyOf(new LinkedHashSet<>(candidates));
            if (sets.size() == 1) {
                ConstraintHints one = sets.get(0);
                return new ConstraintHints(one.lower(), one.upper(), one.earliest(), one.latest(), one.minLength(),
                        one.maxLength(), one.pattern(), one.prefix(), one.excluded(), one.included(),
                        one.minSelected(), one.maxSelected(), null, distinct);
            }
            return new ConstraintHints(null, null, null, null, null, null, null, null, null, null, null, null,
                    sets, distinct);
        } catch (RuntimeException e) {
            return NONE; // the constraint's content never makes this fail
        }
    }

    /** The string literals written in the current question's constraint, in order, without duplicates. */
    static List<String> stringLiterals(FormEntryController session) {
        Set<String> literals = new LinkedHashSet<>();
        try {
            FormEntryModel model = session.getModel();
            FormDef form = model.getForm();
            TreeReference ref = form.getChildInstanceRef(model.getFormIndex());
            AbstractTreeElement node = ref == null ? null : form.getMainInstance().resolveReference(ref);
            if (node instanceof TreeElement element && element.getConstraint() != null
                    && element.getConstraint().constraint instanceof XPathConditional conditional) {
                collectLiterals(conditional.getExpr(), literals);
            }
        } catch (RuntimeException e) {
            // no literals
        }
        return List.copyOf(literals);
    }

    private static void collectLiterals(XPathExpression expr, Set<String> out) {
        if (expr instanceof XPathStringLiteral literal) {
            out.add(literal.s);
        } else if (expr instanceof XPathFuncExpr func) {
            for (XPathExpression arg : func.args) {
                collectLiterals(arg, out);
            }
        } else if (expr instanceof XPathBinaryOpExpr binary) {
            collectLiterals(binary.a, out);
            collectLiterals(binary.b, out);
        } else if (expr instanceof XPathUnaryOpExpr unary) {
            collectLiterals(unary.a, out);
        }
    }

    /** At most this many alternatives are kept (spec 09, Amendment 2). */
    static final int MAX_ALTERNATIVES = 64;

    /** One term of an alternative, possibly negated. */
    private record Term(XPathExpression expr, boolean negated) { }

    /**
     * Turns a constraint into alternatives: conjunctions of terms, any one of which makes the
     * constraint hold (and/or distributed, not() moved inward, if() resolved or expanded).
     */
    private static final class Alternatives {
        private final FormDef form;
        private final TreeReference context;

        Alternatives(FormDef form, TreeReference context) {
            this.form = form;
            this.context = context;
        }

        List<List<Term>> read(XPathExpression expr, boolean negated) {
            if (expr instanceof XPathBoolExpr bool) {
                boolean and = bool.op == XPathBoolExpr.AND;
                List<List<Term>> a = read(bool.a, negated);
                List<List<Term>> b = read(bool.b, negated);
                return and != negated ? product(a, b) : union(a, b);
            }
            if (expr instanceof XPathFuncExpr func) {
                String name = func.id.toString();
                if (name.equals("not") && func.args.length == 1) {
                    return read(func.args[0], !negated);
                }
                if ((name.equals("true") || name.equals("false")) && func.args.length == 0) {
                    return name.equals("true") != negated ? List.of(List.of()) : List.of();
                }
                if (name.equals("if") && func.args.length == 3) {
                    XPathExpression c = func.args[0];
                    if (!usesSelf(c)) {
                        Boolean holds = condition(c);
                        if (holds != null) {
                            return read(holds ? func.args[1] : func.args[2], negated);
                        }
                        return List.of(List.of(new Term(expr, negated)));
                    }
                    return union(product(read(c, false), read(func.args[1], negated)),
                            product(read(c, true), read(func.args[2], negated)));
                }
            }
            return List.of(List.of(new Term(expr, negated)));
        }

        private Boolean condition(XPathExpression c) {
            try {
                Object v = c.eval(form.getMainInstance(), new EvaluationContext(form.getEvaluationContext(), context));
                return XPathFuncExpr.toBoolean(XPathFuncExpr.unpack(v));
            } catch (RuntimeException e) {
                return null;
            }
        }

        private static List<List<Term>> union(List<List<Term>> a, List<List<Term>> b) {
            List<List<Term>> out = new ArrayList<>(a);
            for (List<Term> x : b) {
                if (out.size() >= MAX_ALTERNATIVES) {
                    break;
                }
                out.add(x);
            }
            return out;
        }

        private static List<List<Term>> product(List<List<Term>> a, List<List<Term>> b) {
            List<List<Term>> out = new ArrayList<>();
            for (List<Term> x : a) {
                for (List<Term> y : b) {
                    if (out.size() >= MAX_ALTERNATIVES) {
                        return out;
                    }
                    List<Term> both = new ArrayList<>(x);
                    both.addAll(y);
                    out.add(both);
                }
            }
            return out;
        }
    }

    /** True for the expression {@code .} (self::node()). */
    private static boolean isSelf(XPathExpression expr) {
        return expr instanceof XPathPathExpr path
                && path.init_context == XPathPathExpr.INIT_CONTEXT_RELATIVE
                && path.steps.length == 1
                && path.steps[0].axis == XPathStep.AXIS_SELF
                && path.steps[0].test == XPathStep.TEST_TYPE_NODE
                && (path.steps[0].predicates == null || path.steps[0].predicates.length == 0);
    }

    /** True for {@code .} or {@code string(.)} (the loader wraps select functions' first argument). */
    private static boolean isSelfValue(XPathExpression expr) {
        return isSelf(expr) || expr instanceof XPathFuncExpr func && "string".equals(func.id.toString())
                && func.args.length == 1 && isSelf(func.args[0]);
    }

    /** True for {@code string-length(.)}. */
    private static boolean isLengthOfSelf(XPathExpression expr) {
        return expr instanceof XPathFuncExpr func && "string-length".equals(func.id.toString())
                && func.args.length == 1 && isSelf(func.args[0]);
    }

    /** Whether the expression uses {@code .} anywhere (a relative path starting at the current node). */
    private static boolean usesSelf(XPathExpression expr) {
        if (expr == null) {
            return false;
        }
        if (expr instanceof XPathPathExpr path) {
            if (path.init_context == XPathPathExpr.INIT_CONTEXT_RELATIVE && path.steps.length > 0
                    && path.steps[0].axis == XPathStep.AXIS_SELF) {
                return true;
            }
            if (path.filtExpr != null && usesSelf(path.filtExpr)) {
                return true;
            }
            for (XPathStep step : path.steps) {
                if (anyUsesSelf(step.predicates)) {
                    return true;
                }
            }
            return false;
        }
        if (expr instanceof XPathFuncExpr func) {
            return anyUsesSelf(func.args);
        }
        if (expr instanceof XPathBinaryOpExpr binary) {
            return usesSelf(binary.a) || usesSelf(binary.b);
        }
        if (expr instanceof XPathUnaryOpExpr unary) {
            return usesSelf(unary.a);
        }
        if (expr instanceof XPathFilterExpr filter) {
            return usesSelf(filter.x) || anyUsesSelf(filter.predicates);
        }
        return false;
    }

    private static boolean anyUsesSelf(XPathExpression[] exprs) {
        if (exprs != null) {
            for (XPathExpression e : exprs) {
                if (usesSelf(e)) {
                    return true;
                }
            }
        }
        return false;
    }

    private enum Op { LT, LTE, GT, GTE, EQ, NEQ }

    private static Op negate(Op op) {
        return switch (op) {
            case LT -> Op.GTE;
            case LTE -> Op.GT;
            case GT -> Op.LTE;
            case GTE -> Op.LT;
            case EQ -> Op.NEQ;
            case NEQ -> Op.EQ;
        };
    }

    /** Accumulates the tightest hints over the terms of one constraint. */
    private static final class Builder {
        private final FormDef form;
        private final TreeReference context;
        private Bound lower;
        private Bound upper;
        private DateBound earliest;
        private DateBound latest;
        private Integer minLength;
        private Integer maxLength;
        private String pattern;
        private String prefix;
        private final Set<String> excluded = new LinkedHashSet<>();
        private final Set<String> included = new LinkedHashSet<>();
        private Integer minSelected;
        private Integer maxSelected;
        private final List<String> equalities = new ArrayList<>();

        Builder(FormDef form, TreeReference context) {
            this.form = form;
            this.context = context;
        }

        void term(XPathExpression term, boolean negated) {
            Op op;
            XPathExpression left;
            XPathExpression right;
            if (term instanceof XPathFuncExpr func && "selected".equals(func.id.toString()) && func.args.length == 2
                    && isSelfValue(func.args[0]) && func.args[1] instanceof XPathStringLiteral choice) {
                (negated ? excluded : included).add(choice.s);
                return;
            }
            if (!negated && term instanceof XPathFuncExpr func && "starts-with".equals(func.id.toString())
                    && func.args.length == 2 && isSelfValue(func.args[0]) && !usesSelf(func.args[1])) {
                Object v = evaluate(func.args[1]);
                if (v != null && prefix == null) {
                    prefix = XPathFuncExpr.toString(v);
                }
                return;
            }
            if (term instanceof XPathCmpExpr cmp) {
                op = switch (cmp.op) {
                    case XPathCmpExpr.LT -> Op.LT;
                    case XPathCmpExpr.LTE -> Op.LTE;
                    case XPathCmpExpr.GT -> Op.GT;
                    case XPathCmpExpr.GTE -> Op.GTE;
                    default -> null;
                };
                left = cmp.a;
                right = cmp.b;
            } else if (term instanceof XPathEqExpr eq) {
                op = eq.isEqual() ? Op.EQ : Op.NEQ;
                left = eq.a;
                right = eq.b;
            } else if (!negated && term instanceof XPathFuncExpr func && "regex".equals(func.id.toString())
                    && func.args.length == 2 && isSelf(func.args[0]) && func.args[1] instanceof XPathStringLiteral literal) {
                if (pattern == null) {
                    pattern = literal.s;
                }
                return;
            } else {
                return;
            }
            if (op == null) {
                return;
            }
            if (negated) {
                op = negate(op);
            }
            if (isSelf(left) && !usesSelf(right)) {
                value(op, right);
            } else if (isSelf(right) && !usesSelf(left)) {
                value(flip(op), left);
            } else if (isLengthOfSelf(left) && !usesSelf(right)) {
                length(op, right);
            } else if (isLengthOfSelf(right) && !usesSelf(left)) {
                length(flip(op), left);
            } else if (isCountOfSelf(left) && !usesSelf(right)) {
                count(op, right);
            } else if (isCountOfSelf(right) && !usesSelf(left)) {
                count(flip(op), left);
            } else if (op == Op.EQ && !usesSelf(right)) {
                prefix(left, right);
            } else if (op == Op.EQ && !usesSelf(left)) {
                prefix(right, left);
            }
        }

        /** {@code count-selected(.)}. */
        private static boolean isCountOfSelf(XPathExpression expr) {
            return expr instanceof XPathFuncExpr func && "count-selected".equals(func.id.toString())
                    && func.args.length == 1 && isSelfValue(func.args[0]);
        }

        /** A prefix from {@code substr(., 0, n) = 'p'} with n the length of p. */
        private void prefix(XPathExpression call, XPathExpression text) {
            if (!(call instanceof XPathFuncExpr func) || !"substr".equals(func.id.toString()) || func.args.length != 3
                    || !isSelfValue(func.args[0]) || usesSelf(func.args[1]) || usesSelf(func.args[2])) {
                return;
            }
            Object start = evaluate(func.args[1]);
            Object end = evaluate(func.args[2]);
            Object p = evaluate(text);
            if (start == null || end == null || p == null) {
                return;
            }
            String value = XPathFuncExpr.toString(p);
            if (number(start) == 0 && number(end) == value.length() && prefix == null) {
                prefix = value;
            }
        }

        /** Selection-count bounds from {@code count-selected(.) OP e}, as inclusive integers. */
        private void count(Op op, XPathExpression e) {
            Integer[] range = integers(op, e);
            if (range == null) {
                return;
            }
            if (range[0] != null) {
                minSelected = minSelected == null ? range[0] : Math.max(minSelected, range[0]);
            }
            if (range[1] != null) {
                maxSelected = maxSelected == null ? range[1] : Math.min(maxSelected, range[1]);
            }
        }

        private static Op flip(Op op) {
            return switch (op) {
                case LT -> Op.GT;
                case LTE -> Op.GTE;
                case GT -> Op.LT;
                case GTE -> Op.LTE;
                case EQ -> Op.EQ;
                case NEQ -> Op.NEQ;
            };
        }

        /** A bound on the answer from {@code . OP e}. */
        private void value(Op op, XPathExpression e) {
            Object v = evaluate(e);
            if (v == null) {
                return;
            }
            if (op == Op.NEQ) {
                excluded.add(XPathFuncExpr.toString(v));
                return;
            }
            if (op == Op.EQ) {
                equalities.add(XPathFuncExpr.toString(v));
            }
            if (v instanceof Date date) {
                LocalDate day = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                if (op != Op.LT && op != Op.LTE) {
                    earliest = tighterEarliest(earliest, new DateBound(day, op != Op.GT));
                }
                if (op != Op.GT && op != Op.GTE) {
                    latest = tighterLatest(latest, new DateBound(day, op != Op.LT));
                }
                return;
            }
            double x = number(v);
            if (!Double.isFinite(x)) {
                return;
            }
            if (op != Op.LT && op != Op.LTE) {
                lower = tighterLower(lower, new Bound(x, op != Op.GT));
            }
            if (op != Op.GT && op != Op.GTE) {
                upper = tighterUpper(upper, new Bound(x, op != Op.LT));
            }
        }

        /** Length bounds from {@code string-length(.) OP e}, as inclusive integers. */
        private void length(Op op, XPathExpression e) {
            Integer[] range = integers(op, e);
            if (range == null) {
                return;
            }
            Integer min = range[0];
            Integer max = range[1];
            if (min != null) {
                min = Math.max(0, min);
                minLength = minLength == null ? min : Math.max(minLength, min);
            }
            if (max != null) {
                maxLength = maxLength == null ? max : Math.min(maxLength, max);
            }
        }

        /** Inclusive integer bounds {min, max} (either null) from {@code f OP e}; null when there are none. */
        private Integer[] integers(Op op, XPathExpression e) {
            Object v = evaluate(e);
            if (v == null || v instanceof Date || op == Op.NEQ) {
                return null;
            }
            double x = number(v);
            if (!Double.isFinite(x)) {
                return null;
            }
            Integer min = switch (op) {
                case GT -> (int) Math.floor(x) + 1;
                case GTE, EQ -> (int) Math.ceil(x);
                default -> null;
            };
            Integer max = switch (op) {
                case LT -> (int) Math.ceil(x) - 1;
                case LTE, EQ -> (int) Math.floor(x);
                default -> null;
            };
            if (min != null) {
                min = Math.max(0, min);
            }
            return new Integer[] {min, max};
        }

        /** Values worth trying: this alternative's equalities, then the closed ends of its interval. */
        void addCandidates(List<String> out) {
            out.addAll(equalities);
            if (lower != null && upper != null) {
                double lo = lower.inclusive() ? Math.ceil(lower.value()) : Math.floor(lower.value()) + 1;
                double hi = upper.inclusive() ? Math.floor(upper.value()) : Math.ceil(upper.value()) - 1;
                out.add(XPathFuncExpr.toString(lo));
                out.add(XPathFuncExpr.toString(hi));
            }
        }

        /** The value of {@code e} with the question as context, or null when it cannot be computed. */
        private Object evaluate(XPathExpression e) {
            try {
                Object v = e.eval(form.getMainInstance(), new EvaluationContext(form.getEvaluationContext(), context));
                return XPathFuncExpr.unpack(v);
            } catch (RuntimeException ex) {
                return null;
            }
        }

        private static double number(Object v) {
            try {
                return XPathFuncExpr.toNumeric(v);
            } catch (RuntimeException ex) {
                return Double.NaN;
            }
        }

        ConstraintHints build() {
            return new ConstraintHints(lower, upper, earliest, latest, minLength, maxLength, pattern,
                    prefix, excluded, included, minSelected, maxSelected, null, null);
        }
    }

    private static Bound tighterLower(Bound current, Bound candidate) {
        if (current == null || candidate.value() > current.value()
                || (candidate.value() == current.value() && !candidate.inclusive())) {
            return candidate;
        }
        return current;
    }

    private static Bound tighterUpper(Bound current, Bound candidate) {
        if (current == null || candidate.value() < current.value()
                || (candidate.value() == current.value() && !candidate.inclusive())) {
            return candidate;
        }
        return current;
    }

    private static DateBound tighterEarliest(DateBound current, DateBound candidate) {
        if (current == null || candidate.value().isAfter(current.value())
                || (candidate.value().equals(current.value()) && !candidate.inclusive())) {
            return candidate;
        }
        return current;
    }

    private static DateBound tighterLatest(DateBound current, DateBound candidate) {
        if (current == null || candidate.value().isBefore(current.value())
                || (candidate.value().equals(current.value()) && !candidate.inclusive())) {
            return candidate;
        }
        return current;
    }
}
