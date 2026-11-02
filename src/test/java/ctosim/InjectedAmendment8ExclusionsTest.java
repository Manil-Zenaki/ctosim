package ctosim;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static ctosim.FormWalkerTestSupport.*;
import static ctosim.InjectedAmendment8Support.*;
import static ctosim.InjectedProblemsTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Isolated("Walks change the JVM time zone")
class InjectedAmendment8ExclusionsTest {
    private static String companionNodes() { return "<answer/><specify/>"; }
    private static String companionBinds() { return bind("answer", "select1", "") + bind("specify", "string", relevant("answer", "O")); }
    private static String companionBody() { return select("answer", false, "a", "abc", "O", "Other") + input("specify"); }

    @ParameterizedTest @ValueSource(strings = {"readonly", "calculated"})
    void readonlyAndCalculatedSelectAnswersRemainProtected(String mode) throws Exception {
        var f = fixture("<protected>a</protected><detail/>" + companionNodes(),
                bind("protected", "select1", mode.equals("readonly") ? "readonly=\"true()\"" : "calculate=\"'a'\"")
                        + bind("detail", "string", relevant("protected", "O")) + companionBinds(),
                select("protected", false, "a", "xyz", "O", "Other") + input("detail") + companionBody());
        var r = other(f, 600, codes("O") + "," + fixed("answer", "a"));
        unchanged(r, "protected", "a"); singleRecords(r, "a", Set.of("O"));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void directAndIndirectSelectRepeatCountControllersRemainProtected(boolean indirect) throws Exception {
        var f = fixture("<size/><detail/><total/><person jr:template=\"\"><name>kept</name></person>" + companionNodes(),
                bind("size", "select1", "") + bind("detail", "string", relevant("size", "1"))
                        + bind("total", "int", "calculate=\"number(/data/size)\"")
                        + bind("person/name", "string", "readonly=\"true()\"") + companionBinds(),
                select("size", false, "3", "Three", "1", "Other") + input("detail")
                        + "<repeat nodeset=\"/data/person\" jr:count=\"/data/" + (indirect ? "total" : "size") + "\">"
                        + input("person/name") + "</repeat>" + companionBody());
        var r = other(f, 600, codes("1", "O") + "," + fixed("size", "3", "answer", "a"));
        unchanged(r, "size", "3"); singleRecords(r, "a", Set.of("O"));
        for (var s : r.submissions()) assertEquals(3, count(s, "person"));
    }

    @Test void addAnotherSelectControllingADynamicRepeatCountRemainsProtected() throws Exception {
        var f = fixture("<total/><person jr:template=\"\"><more/><detail/><flag/></person>" + companionNodes(),
                bind("total", "int", "calculate=\"min(sum(/data/person/flag) + 1, 2)\"")
                        + bind("person/more", "select1", "")
                        + bind("person/detail", "string", "relevant=\"../more = 'O'\"")
                        + bind("person/flag", "int", "calculate=\"if(../more = 'yes', 1, 0)\"") + companionBinds(),
                "<repeat nodeset=\"/data/person\" jr:count=\"/data/total\">"
                        + select("person/more", false, "yes", "Yes", "O", "Other") + input("person/detail")
                        + "</repeat>" + companionBody());
        var r = other(f, 600, codes("O") + "," + fixed("more", "yes", "answer", "a"));
        singleRecords(r, "a", Set.of("O"));
        for (var s : r.submissions()) {
            assertEquals(2, count(s, "person"));
            assertEquals("yes", value(s, "person[1]/more")); assertEquals("yes", value(s, "person[2]/more"));
        }
    }

    @Test void consentSelectsRemainProtectedEvenWhenTheirRefusalIsAnOtherCode() throws Exception {
        var f = fixture("<understand/><us/><agree/><as/>" + companionNodes(),
                bind("understand", "select1", "") + bind("us", "string", relevant("understand", "non"))
                        + bind("agree", "select1", "") + bind("as", "string", relevant("agree", "non")) + companionBinds(),
                select("understand", false, "oui", "Yes", "non", "Other") + input("us")
                        + select("agree", false, "oui", "Yes", "non", "Other") + input("as") + companionBody());
        var r = other(f, 600, codes("non", "O") + "," + InjectedAmendment2Support.consent(.5) + "," + fixed("answer", "a"));
        unchanged(r, "understand", "oui"); singleRecords(r, "a", Set.of("O"));
        Set<String> seen = new HashSet<>();
        for (var s : r.submissions()) {
            String expected = value(r.clean(s.number()), "agree"); seen.add(expected); assertEquals(expected, value(s, "agree"));
        }
        assertEquals(Set.of("oui", "non"), seen);
    }

    @ParameterizedTest @ValueSource(strings = {"int", "decimal", "string"})
    void nonselectInputsAreNeverTreatedAsOtherQuestions(String type) throws Exception {
        var f = fixture("<protected/><detail/>" + companionNodes(), bind("protected", type, "")
                + bind("detail", "string", "relevant=\"/data/protected = '96'\"") + companionBinds(),
                input("protected") + input("detail") + companionBody());
        var r = other(f, 600, codes("96", "O") + "," + fixed("protected", "12", "answer", "a"));
        unchanged(r, "protected", "12"); singleRecords(r, "a", Set.of("O"));
    }
}
