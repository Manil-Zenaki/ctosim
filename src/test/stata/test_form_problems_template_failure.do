version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_form_problems_template_failure" "double"
    exit
}
do "$CTOSIM_TESTDIR/form-problems-helpers.do"
copy "$CTOSIM_TESTDIR/fixtures/form_problems_import_failure.do" "input files/failing template.do", replace
local original `"`c(pwd)'"'
foreach scenario in errors constraints both clean {
    local e = cond(inlist("`scenario'", "constraints", "clean"), 0, 3)
    local u = cond(inlist("`scenario'", "errors", "clean"), 0, 2)
    a6_case `e' `u' 2 0
    ct_memory
    quietly log using "failure-`scenario'-tail.smcl", smcl replace name(a6tail)
    quietly log off a6tail
    quietly log using "failure-`scenario'.smcl", smcl replace name(a6)
    capture noisily ctosim using "input files/survey.xml", saving("failure `scenario'") ///
        dofile("input files/failing template.do") clear
    local rc = _rc
    quietly log close a6
    quietly log close a6tail
    assert `rc' == 459
    assert `"`c(pwd)'"' == `"`original'"'
    assert _N == 1
    assert template_partial == 73
    local label : data label
    assert "`label'" == "Amendment 6 partial import survives"
    // r() after failure is unspecified. Check report-derived counts in the message.
    a6_log "failure-`scenario'.smcl" "failure `scenario'" full 459
    a6_log "failure-`scenario'-tail.smcl" "failure `scenario'" tail 459
}

