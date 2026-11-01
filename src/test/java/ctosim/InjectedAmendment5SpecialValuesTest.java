package ctosim;

import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.InjectedAmendment1Support.*;
import static ctosim.InjectedAmendment2Support.*;
import static ctosim.InjectedAmendment5Support.*;
import static ctosim.InjectedProblemsTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Walks change the JVM time zone")
class InjectedAmendment5SpecialValuesTest {
    @ParameterizedTest @EnumSource(value = QuestionKind.class, names = {"SELECT_ONE", "SELECT_MULTIPLE"})
    void ordinarySpecialAnswersStillComeOnlyFromSpecialValues(QuestionKind kind) throws Exception {
        var config = SimulationConfig.parse("{\"missingRate\":0,\"specialRate\":1,"
                + "\"specialValues\":[\"97\"]," + codes("DK") + "}", "amendment5-ordinary.json");
        var generator = new AnswerGenerator(config);
        var question = new Question("answer", kind, List.of("ok", "97", "DK"), "", false);
        var random = new Random(713284);
        for (int i = 0; i < 200; i++) {
            assertEquals("97", generator.generate(question, LocalDate.of(2026, 3, 2), random));
        }
    }

    @Test void aTeamCodeOutsideSpecialValuesRemainsAnOrdinarySelectChoice() throws Exception {
        var config = SimulationConfig.parse("{\"missingRate\":0,\"specialRate\":0,"
                + "\"specialValues\":[\"97\"]," + codes("DK") + "}", "amendment5-ordinary.json");
        var generator = new AnswerGenerator(config);
        var question = new Question("answer", QuestionKind.SELECT_ONE, List.of("ok", "97", "DK"), "", false);
        var random = new Random(713284);
        int teamCodes = 0;
        for (int i = 0; i < 1600; i++) {
            String value = generator.generate(question, LocalDate.of(2026, 3, 2), random);
            assertTrue(List.of("ok", "DK").contains(value));
            if (value.equals("DK")) teamCodes++;
        }
        rate(teamCodes, 1600, .5);
    }

    @ParameterizedTest @EnumSource(value = QuestionKind.class,
            names = {"SELECT_ONE", "SELECT_MULTIPLE", "INTEGER", "DECIMAL"})
    void merelyNamingTeamCodesDoesNotChangeTheOrdinaryGeneratorSequence(QuestionKind kind) throws Exception {
        String extra = "\"specialValues\":[\"97\"]";
        var before = new AnswerGenerator(settings(1, 1, extra));
        var after = new AnswerGenerator(settings(1, 1, extra + "," + codes("DK", "-888")));
        var question = new Question("answer", kind,
                kind == QuestionKind.SELECT_ONE || kind == QuestionKind.SELECT_MULTIPLE
                        ? List.of("ok", "97", "DK") : List.of(), "", false);
        var first = new Random(82913);
        var second = new Random(82913);
        for (int i = 0; i < 200; i++) {
            assertEquals(before.generate(question, LocalDate.of(2026, 3, 2), first),
                    after.generate(question, LocalDate.of(2026, 3, 2), second));
        }
    }

    @Test void shorteningARosterAvoidsSpecialValuesButMayUseANonspecialTeamCode() throws Exception {
        var form = extending(true, false, items("yes", "97", "DK"));
        var config = settings(1200, 2, "\"specialValues\":[\"97\"]," + codes("DK") + "," + fixed("more", "yes"));
        var run = run(form, config.withProblems(rates(0, "skipModules")));
        var records = records(run, "skipModules");
        rate(records.size(), 600, .25);
        assertFalse(records.isEmpty());
        for (var record : records) {
            assertEquals("person", record.field());
            assertEquals("2", record.cleanValue());
            assertEquals("1", record.value());
            assertEquals(1, count(run.target(record), "person"));
            assertEquals("DK", value(run.target(record), "person[1]/more"));
        }
        assertTrue(records(run, "dontKnow").isEmpty(), "dontKnow is not requested");
    }

    @Test void aSpecialOnlyStoppingChoiceCannotShortenARosterEvenWhenTeamCodesDiffer() throws Exception {
        var form = extending(true, false, items("yes", "97"));
        var run = run(form, settings(200, 2, "\"specialValues\":[\"97\"],"
                + codes("DK") + "," + fixed("more", "yes")).withProblems(rates(0, "skipModules")));
        assertTrue(run.plan().problems().isEmpty());
        for (var submission : run.submissions()) {
            assertEquals(2, count(submission, "person"));
            assertEquals("yes", value(submission, "person[1]/more"));
        }
    }
}
