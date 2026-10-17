version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_unsatisfied_template" "double"
    exit
}
do "$CTOSIM_TESTDIR/unsatisfied-helpers.do"
a4_case 3
ct_memory
copy "$CTOSIM_TESTDIR/fixtures/unsatisfied_import.do" "input files/template.do", replace
local original `"`c(pwd)'"'
quietly log using "template.smcl", smcl replace name(a4)
ctosim using "input files/survey.xml", saving("template output") ///
    dofile("input files/template.do") clear
a4_results 3 27 "template output"
quietly log close a4
assert `"`c(pwd)'"' == `"`original'"'
assert _N == 2
assert imported_by_a4_template == 1
assert id == _n
assert missing(answer)
a4_log "template.smcl" "template output"
ct_absent "template.smcl" "A4_TEMPLATE_OUTPUT_MUST_BE_SUPPRESSED"
