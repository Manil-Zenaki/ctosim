package ctosim;

import static ctosim.SurveyCtoTestSupport.*;

final class SurveyCtoNumericSelectTestSupport {
    private SurveyCtoNumericSelectTestSupport() {}

    static LoadedForm numericForm(String resultBind, String targetBind, String action) throws Exception {
        return load(fixture("numeric-select-template.xml")
                .replace("__RESULT_BIND__", resultBind)
                .replace("__TARGET_BIND__", targetBind)
                .replace("__ACTION__", action));
    }

    static LoadedForm calculation(String expression) throws Exception {
        return numericForm("calculate=\"" + attribute(bracket(expression)) + "\"", "", "");
    }

    // Prevent answer storage from converting a string such as "1" into IntegerData.
    static String bracket(String expression) {
        return "concat('[', " + expression + ", ']')";
    }
}
