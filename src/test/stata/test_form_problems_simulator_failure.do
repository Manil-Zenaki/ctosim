version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_form_problems_simulator_failure" "double"
    exit
}
do "$CTOSIM_TESTDIR/form-problems-helpers.do"
// A stale report must not become a new final diagnostic on simulator failure.
a6_case 3 2 2 0
ctosim using "input files/survey.xml", saving("stale report") clear
foreach status in 1 2 17 {
    a6_case 3 2 2 0 `status'
    ct_memory
    local original `"`c(pwd)'"'
    quietly log using "simulator-`status'.log", text replace name(a6)
    capture noisily ctosim using "input files/survey.xml", saving("stale report") clear replace
    local rc = _rc
    quietly log close a6
    assert `rc' == cond(`status' == 2, 198, 498)
    ct_memory_unchanged
    assert `"`c(pwd)'"' == `"`original'"'
    ct_contains "simulator-`status'.log" "A6_SIMULATOR_FAILURE_`status'"
    ct_absent "simulator-`status'.log" "FORM ERRORS"
    ct_absent "simulator-`status'.log" "form has problems"
    ct_absent "simulator-`status'.log" "run-report.txt"
}

