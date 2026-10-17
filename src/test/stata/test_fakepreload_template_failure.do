version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_fakepreload_template_failure" "double"
    exit
}
do "$CTOSIM_TESTDIR/fakepreload-helpers.do"
a5_case many 2 1
ct_memory
local original `"`c(pwd)'"'
quietly log using "failed-template.log", text replace name(a5)
capture noisily ctosim using "input files/survey.xml", saving("failed template") ///
    fakepreload dofile("$CTOSIM_TESTDIR/fixtures/import_failure.do") clear
local rc = _rc
quietly log close a5
assert `rc' == 459
assert `"`c(pwd)'"' == `"`original'"'
assert _N == 1
assert template_partial == 73
local label : data label
assert "`label'" == "Partial import must survive"
a5_log "failed-template.log" "failed template"
ct_absent "failed-template.log" "FAILING_IMPORT_OUTPUT_MUST_BE_SUPPRESSED_11"
