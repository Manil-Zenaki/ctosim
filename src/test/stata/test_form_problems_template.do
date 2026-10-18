version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_form_problems_template" "double"
    exit
}
do "$CTOSIM_TESTDIR/form-problems-helpers.do"
copy "$CTOSIM_TESTDIR/fixtures/form_problems_import.do" "input files/template.do", replace
local original `"`c(pwd)'"'
foreach scenario in errors constraints both clean {
    local e = cond(inlist("`scenario'", "constraints", "clean"), 0, 3)
    local u = cond(inlist("`scenario'", "errors", "clean"), 0, 2)
    a6_case `e' `u' 2 1
    ct_memory
    quietly log using "template-`scenario'-tail.smcl", smcl replace name(a6tail)
    quietly log off a6tail
    quietly log using "template-`scenario'.smcl", smcl replace name(a6)
    ctosim using "input files/survey.xml", saving("template `scenario'") ///
        dofile("input files/template.do") fakepreload clear
    quietly log close a6
    quietly log close a6tail
    a6_results `e' `u' 2 "template `scenario'" "people.csv villages.csv"
    assert `"`c(pwd)'"' == `"`original'"'
    assert _N == 2
    assert id == _n
    assert imported_by_a6_template == 1
    local label : data label
    assert "`label'" == "Amendment 6 template completed"
    a6_log "template-`scenario'.smcl" "template `scenario'"
    // An early summary cannot satisfy this log: the do-file enabled it at its end.
    a6_log "template-`scenario'-tail.smcl" "template `scenario'" tail
}

