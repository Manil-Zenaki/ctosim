version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_missing_converter" "missing_converter"
    exit
}
do "$CTOSIM_TESTDIR/helpers.do"
ct_scenario 0
ct_memory
quietly log using "missing.log", text replace name(capture11)
capture noisily ctosim using "input files/survey.xlsx", saving("missing") clear
local rc = _rc
quietly log close capture11
assert `rc' == 601
ct_not_invoked
ct_memory_unchanged
javacall StataTestSupport missingDiagnostic, jars(stata-test-support.jar) args("$CT_TEST_WORK/missing.log" "converter")
