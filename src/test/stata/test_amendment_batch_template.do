version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/amendment-launch.do" "test_amendment_batch_template" "batch"
    exit
}
do "$CTOSIM_TESTDIR/amendment-helpers.do"
ct_scenario 0 1
ct_memory
copy "$CTOSIM_TESTDIR/fixtures/import_success.do" "input files/import template.do", replace
local original `"`c(pwd)'"'
quietly log using "batch-template.log", text replace name(amendment)
ctosim using "input files/survey.xml", saving("batch template output") ///
    dofile("input files/import template.do") clear
ct_report_results 3 -713 1 "batch template output"
quietly log close amendment
ct_invoked
ct_amendment_selected "override"
assert _N == 2
assert imported_by_template == 1
assert `"`c(pwd)'"' == `"`original'"'
ct_absent "batch-template.log" "IMPORT_OUTPUT_MUST_BE_SUPPRESSED_11"
ct_contains "batch-template.log" "SIMULATOR_STDOUT_11"
javacall StataTestSupport warningsInLog, jars(stata-test-support.jar) ///
    args("$CT_TEST_WORK/batch-template.log" "$CT_TEST_WORK/batch template output/run-report.txt")
