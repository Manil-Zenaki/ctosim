version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_injected_problems_template_failure" "double"
    exit
}
do "$CTOSIM_TESTDIR/injected-problems-helpers.do"
a8_case 2 1
local original `"`c(pwd)'"'
quietly log using "template-failure.log", text replace name(a8)
capture noisily ctosim using "input files/survey.xml", problems saving("failed template") ///
    dofile("$CTOSIM_TESTDIR/fixtures/import_failure.do") clear
local rc = _rc
quietly log close a8
assert `rc' == 459
assert `"`c(pwd)'"' == `"`original'"'
assert _N == 1
assert template_partial == 73
quietly describe
assert r(k) == 1
ct_absent "template-failure.log" "FAILING_IMPORT_OUTPUT_MUST_BE_SUPPRESSED_11"
a8_log "template-failure.log" 2 1
