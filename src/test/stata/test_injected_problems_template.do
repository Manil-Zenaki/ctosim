version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_injected_problems_template" "double"
    exit
}
do "$CTOSIM_TESTDIR/injected-problems-helpers.do"
copy "$CTOSIM_TESTDIR/fixtures/import_success.do" "input files/import.do", replace
a8_case 1 2
local original `"`c(pwd)'"'
quietly log using "template.log", text replace name(a8)
ctosim using "input files/survey.xml", problems saving("template output") ///
    dofile("input files/import.do") clear
a8_results 1 2 "template output"
quietly log close a8
assert `"`c(pwd)'"' == `"`original'"'
assert _N == 2
assert imported_by_template == 1
foreach v in family problem check field value clean_value related_key kind {
    capture confirm variable `v'
    assert _rc != 0
}
local label : data label
assert "`label'" == "Data left by SurveyCTO template"
ct_absent "template.log" "IMPORT_OUTPUT_MUST_BE_SUPPRESSED_11"
a8_log "template.log" 1 2
