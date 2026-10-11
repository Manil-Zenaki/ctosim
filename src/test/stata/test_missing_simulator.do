version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_missing_simulator" "missing_jar"
    exit
}
do "$CTOSIM_TESTDIR/helpers.do"
ct_scenario 0
ct_memory
quietly log using "missing.log", text replace name(capture11)
capture noisily ctosim using "input files/survey.xml", saving("missing") clear
local rc = _rc
quietly log close capture11
assert `rc' == 601
ct_not_invoked
ct_memory_unchanged
// A valid jar exists on the ado-path: a nonempty override must still win.
javacall StataTestSupport missingDiagnostic, jars(stata-test-support.jar) args("$CT_TEST_WORK/missing.log" "simulator")
