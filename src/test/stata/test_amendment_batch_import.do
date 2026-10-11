version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/amendment-launch.do" "test_amendment_batch_import" "batch"
    exit
}
do "$CTOSIM_TESTDIR/amendment-helpers.do"
// This child was launched with /e do: Windows shell is unavailable here.
// Actual output, data, diagnostics, and stored results must still arrive.
import delimited using "$CTOSIM_TESTDIR/fixtures/wide.csv", clear ///
    varnames(1) encoding("utf-8") bindquotes(strict)
save "expected.dta", replace
ct_memory
ct_scenario 0 2
local original `"`c(pwd)'"'
quietly log using "batch.log", text replace name(amendment)
ctosim using "input files/survey.xml", n(3) seed(-713) saving("batch output with spaces") clear
ct_report_results 3 -713 2 "batch output with spaces"
quietly log close amendment
ct_invoked
ct_amendment_selected "override"
cf _all using "expected.dta", all
assert _N == 3
assert `"`c(pwd)'"' == `"`original'"'
ct_argument "-n" "3"
ct_argument "-s" "-713"
ct_contains "batch.log" "SIMULATOR_STDOUT_11"
ct_contains "batch.log" "SIMULATOR_STDERR_11"
javacall StataTestSupport warningsInLog, jars(stata-test-support.jar) ///
    args("$CT_TEST_WORK/batch.log" "$CT_TEST_WORK/batch output with spaces/run-report.txt")
