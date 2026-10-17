version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_fakepreload_template" "double"
    exit
}
do "$CTOSIM_TESTDIR/fakepreload-helpers.do"
a5_case one 2
ct_memory
copy "$CTOSIM_TESTDIR/fixtures/fakepreload_import.do" "input files/template.do", replace
local original `"`c(pwd)'"'
quietly log using "template.log", text replace name(a5)
ctosim using "input files/survey.xml", saving("template output") ///
    fakepreload dofile("input files/template.do") clear
a5_results "people.csv" 2 0 "template output"
quietly log close a5
assert `"`c(pwd)'"' == `"`original'"'
assert _N == 2
assert imported_by_a5_template == 1
assert id == _n
a5_log "template.log" "template output"
ct_absent "template.log" "A5_TEMPLATE_OUTPUT_MUST_BE_SUPPRESSED"
