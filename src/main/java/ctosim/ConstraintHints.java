package ctosim;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
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
                              Integer minLength, Integer maxLength, String pattern) {

    private static final ConstraintHints NONE = new ConstraintHints(null, null, null, null, null, null, null);

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
            Builder hints = new Builder(form, ref);
            List<XPathExpression> terms = new ArrayList<>();
            conjunction(conditional.getExpr(), terms);
            for (XPathExpression term : terms) {
                hints.term(term);
            }
            return hints.build();
        } catch (RuntimeException e) {
            return NONE; // the constraint's content never makes this fail
        }
    }

    /** The terms of a top-level conjunction, nested conjunctions flattened. */
    private static void conjunction(XPathExpression expr, List<XPathExpression> terms) {
        if (expr instanceof XPathBoolExpr bool && bool.op == XPathBoolExpr.AND) {
            conjunction(bool.a, terms);
            conjunction(bool.b, terms);
        } else {
            terms.add(expr);
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

    private enum Op { LT, LTE, GT, GTE, EQ }

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

        Builder(FormDef form, TreeReference context) {
            this.form = form;
            this.context = context;
        }

        void term(XPathExpression term) {
            Op op;
            XPathExpression left;
            XPathExpression right;
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
            } else if (term instanceof XPathEqExpr eq && eq.isEqual()) {
                op = Op.EQ;
                left = eq.a;
                right = eq.b;
            } else if (term instanceof XPathFuncExpr func && "regex".equals(func.id.toString())
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
            if (isSelf(left) && !usesSelf(right)) {
                value(op, right);
            } else if (isSelf(right) && !usesSelf(left)) {
                value(flip(op), left);
            } else if (isLengthOfSelf(left) && !usesSelf(right)) {
                length(op, right);
            } else if (isLengthOfSelf(right) && !usesSelf(left)) {
                length(flip(op), left);
            }
        }

        private static Op flip(Op op) {
            return switch (op) {
                case LT -> Op.GT;
                case LTE -> Op.GTE;
                case GT -> Op.LT;
                case GTE -> Op.LTE;
                case EQ -> Op.EQ;
            };
        }

        /** A bound on the answer from {@code . OP e}. */
        private void value(Op op, XPathExpression e) {
            Object v = evaluate(e);
            if (v == null) {
                return;
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
            Object v = evaluate(e);
            if (v == null || v instanceof Date) {
                return;
            }
            double x = number(v);
            if (!Double.isFinite(x)) {
                return;
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
                minLength = minLength == null ? min : Math.max(minLength, min);
            }
            if (max != null) {
                maxLength = maxLength == null ? max : Math.min(maxLength, max);
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
            return new ConstraintHints(lower, upper, earliest, latest, minLength, maxLength, pattern);
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
