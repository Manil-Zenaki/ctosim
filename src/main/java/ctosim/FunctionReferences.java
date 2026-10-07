package ctosim;

import java.util.Set;
import java.util.TreeSet;
import org.javarosa.xpath.XPathParseTool;
import org.javarosa.xpath.expr.XPathBinaryOpExpr;
import org.javarosa.xpath.expr.XPathExpression;
import org.javarosa.xpath.expr.XPathFilterExpr;
import org.javarosa.xpath.expr.XPathFuncExpr;
import org.javarosa.xpath.expr.XPathPathExpr;
import org.javarosa.xpath.expr.XPathStep;
import org.javarosa.xpath.expr.XPathUnaryOpExpr;
import org.javarosa.xpath.parser.XPathSyntaxException;

/** Collects the names of the functions called anywhere in XPath expressions. */
final class FunctionReferences {

    private final Set<String> names = new TreeSet<>();

    /** Parses an expression and records every function it calls, including nested and unreached calls. */
    void add(String expression) throws XPathSyntaxException {
        visit(XPathParseTool.parseXPath(expression));
    }

    Set<String> names() {
        return names;
    }

    private void visit(XPathExpression expr) {
        if (expr instanceof XPathFuncExpr func) {
            names.add(func.id.toString());
            visitAll(func.args);
        } else if (expr instanceof XPathBinaryOpExpr binary) {
            visit(binary.a);
            visit(binary.b);
        } else if (expr instanceof XPathUnaryOpExpr unary) {
            visit(unary.a);
        } else if (expr instanceof XPathFilterExpr filter) {
            visit(filter.x);
            visitAll(filter.predicates);
        } else if (expr instanceof XPathPathExpr path) {
            if (path.filtExpr != null) {
                visit(path.filtExpr);
            }
            if (path.steps != null) {
                for (XPathStep step : path.steps) {
                    visitAll(step.predicates);
                }
            }
        }
    }

    private void visitAll(XPathExpression[] exprs) {
        if (exprs != null) {
            for (XPathExpression e : exprs) {
                visit(e);
            }
        }
    }
}
