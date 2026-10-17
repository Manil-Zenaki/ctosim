version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_unsatisfied_template_failure" "double"
    exit
}
do "$CTOSIM_TESTDIR/unsatisfied-helpers.do"
a4_case 1
ct_memory
local original `"`c(pwd)'"'
quietly log using "template-failure.smcl", smcl replace name(a4)
capture noisily ctosim using "input files/survey.xml", saving("failed template") ///
    dofile("$CTOSIM_TESTDIR/fixtures/import_failure.do") clear
local rc = _rc
quietly log close a4
assert `rc' == 459
assert `"`c(pwd)'"' == `"`original'"'
assert _N == 1
assert template_partial == 73
local label : data label
assert "`label'" == "Partial import must survive"
a4_log "template-failure.smcl" "failed template"
ct_absent "template-failure.smcl" "FAILING_IMPORT_OUTPUT_MUST_BE_SUPPRESSED_11"
