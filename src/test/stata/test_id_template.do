version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_id_template" "double"
    exit
}
do "$CTOSIM_TESTDIR/id-helpers.do"
copy "$CTOSIM_TESTDIR/fixtures/import_success.do" "input files/import.do", replace
ct_scenario 0 2
local original `"`c(pwd)'"'
quietly log using "id-template.log", text replace name(idtemplate)
ctosim using "input files/survey.xml", id(quantity) saving("template output") ///
    dofile("input files/import.do") clear
ct_report_results 3 -713 2 "template output"
quietly log close idtemplate
assert _N == 2
assert imported_by_template == 1
assert `"`c(pwd)'"' == `"`original'"'
a7_id_argument "quantity"
ct_absent "id-template.log" "IMPORT_OUTPUT_MUST_BE_SUPPRESSED_11"

ct_scenario 0
capture noisily ctosim using "input files/survey.xml", id(quantity) saving("failed template") ///
    dofile("$CTOSIM_TESTDIR/fixtures/import_failure.do") clear
assert _rc == 459
assert `"`c(pwd)'"' == `"`original'"'
confirm variable template_partial
a7_id_argument "quantity"
