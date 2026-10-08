package ctosim;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.data.IntegerData;
import org.javarosa.core.model.data.SelectOneData;
import org.javarosa.core.model.data.helper.Selection;
import org.javarosa.core.model.instance.TreeElement;
import org.javarosa.form.api.FormEntryController;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static ctosim.SurveyCtoTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SurveyCtoNumericSelectRealFormTest {
    private static final String ASPIRATIONS = "/data/interview_begin/migration_aspirations/";

    static Stream<Arguments> treatmentArms() {
        return Stream.of(Arguments.of(1, 1), Arguments.of(1, 2), Arguments.of(2, 1), Arguments.of(2, 2));
    }

    @ParameterizedTest(name = "published form: direct/list={0}, intervention/control={1}")
    @MethodSource("treatmentArms")
    void publishedTreatmentRelevancesEvaluateWithoutExceptionsAndFollowStoredNumbers(int direct, int intervention) throws Exception {
        var loaded = FormLoader.load(Path.of("samples", "xforms", "ethiopia_migration_household.xml"));
        var session = loaded.newEntryController();
        var form = session.getModel().getForm();
        // Make the treatment source fields relevant; JavaRosa otherwise masks
        // references to their values as empty even when a stored answer exists.
        form.setValue(new SelectOneData(new Selection("1")), child(root(session), "availability_04").getRef(), true);
        form.setValue(new SelectOneData(new Selection("1")), child(root(session), "introduce_05").getRef(), true);
        form.setValue(new IntegerData(1), child(root(session), "hh_count_06").getRef(), true);
        form.setValue(new SelectOneData(new Selection("1")), child(root(session), "hh_one_exist").getRef(), true);
        form.setValue(new SelectOneData(new Selection("1")), child(root(session), "consent").getRef(), true);
        var group = child(root(session), "interview_begin");
        assertTrue(group.isRelevant(), "Interview gate must expose the numeric treatment fields");
        var directNode = child(group, "direct_or_list");
        var interventionNode = child(group, "intervention_or_control");
        // Force each arm deterministically through the engine, not by modifying XML
        // or assigning instance nodes directly. once() retains the supplied number.
        assertDoesNotThrow(() -> form.setValue(new IntegerData(direct), directNode.getRef(), true));
        assertDoesNotThrow(() -> form.setValue(new IntegerData(intervention), interventionNode.getRef(), true));
        assertValue(Integer.valueOf(direct), value(directNode));
        assertValue(Integer.valueOf(intervention), value(interventionNode));

        Map<String, Boolean> directExpected = Map.of(
                ASPIRATIONS + "consider_irreg1", direct == 1,
                ASPIRATIONS + "list_1", direct == 2,
                ASPIRATIONS + "consider_irreg2", direct == 2,
                ASPIRATIONS + "list_2", direct == 1);
        assertStoredConditions(session, directNode, directExpected);

        Map<String, Boolean> interventionExpected = new LinkedHashMap<>();
        for (int list = 1; list <= 2; list++) {
            String prefix = ASPIRATIONS + "list_" + list + "/" + (list == 1 ? "first" : "second") + "_set_";
            interventionExpected.put(prefix + "1_Asen", intervention == 1);
            interventionExpected.put(prefix + "1_Bcon", intervention == 1);
            interventionExpected.put(prefix + "2_Acon", intervention == 2);
            interventionExpected.put(prefix + "2_Bsen", intervention == 2);
        }
        assertStoredConditions(session, interventionNode, interventionExpected);
    }

    private static void assertStoredConditions(FormEntryController session, TreeElement source,
            Map<String, Boolean> expected) {
        var form = session.getModel().getForm();
        var triggered = assertDoesNotThrow(() -> form.triggerTriggerables(source.getRef()));
        for (var entry : expected.entrySet()) {
            TreeElement target = nodeAt(session, entry.getKey());
            // Evaluate the existing form condition, not a newly parsed selected()
            // call (the latter is explicitly outside Amendment 2). Evaluate the
            // own relevance independently of unrelated ancestor gating answers.
            var matches = triggered.stream().filter(t -> t.isCondition()
                    && t.getTriggerable().getTargets().stream()
                            .anyMatch(ref -> ref.equals(target.getRef().genericize()))).toList();
            assertEquals(1, matches.size(), "Expected one stored treatment condition for " + entry.getKey());
            var expression = matches.get(0).getTriggerable().getExpr();
            var context = new EvaluationContext(form.getEvaluationContext(), target.getRef());
            Object actual = assertDoesNotThrow(() -> expression.evalRaw(form.getMainInstance(), context), entry.getKey());
            assertValue(entry.getValue(), actual);
        }
    }

    private static TreeElement nodeAt(FormEntryController session, String path) {
        TreeElement node = root(session);
        for (String part : path.substring("/data/".length()).split("/")) node = child(node, part);
        return node;
    }
}
