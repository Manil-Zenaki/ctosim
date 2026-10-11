version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_dofile_failure" "double"
    exit
}
do "$CTOSIM_TESTDIR/helpers.do"
ct_scenario 0
ct_memory
local original `"`c(pwd)'"'
quietly log using "failed_import.log", text replace name(capture11)
capture noisily ctosim using "input files/survey.xml", saving("partial output") ///
    dofile("$CTOSIM_TESTDIR/fixtures/import_failure.do") clear
local rc = _rc
quietly log close capture11
assert `rc' == 459
assert `"`c(pwd)'"' == `"`original'"'
assert _N == 1
assert template_partial == 73
local label : data label
assert "`label'" == "Partial import must survive"
capture confirm variable keeper
assert _rc != 0
ct_absent "failed_import.log" "FAILING_IMPORT_OUTPUT_MUST_BE_SUPPRESSED_11"
confirm file "partial output/run-report.txt"
