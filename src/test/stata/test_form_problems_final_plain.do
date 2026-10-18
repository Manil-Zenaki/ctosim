version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_form_problems_final_plain" "double"
    exit
}
do "$CTOSIM_TESTDIR/form-problems-helpers.do"
// Exercise both sides of the OR condition independently, and differing counts.
foreach scenario in errors constraints both {
    local e = cond("`scenario'" == "constraints", 0, 3)
    local u = cond("`scenario'" == "errors", 0, 2)
    a6_case `e' `u' 2 0
    ct_memory
    quietly log using "plain-`scenario'.smcl", smcl replace name(a6)
    ctosim using "input files/survey.xml", saving("plain `scenario'") clear
    quietly log close a6
    a6_results `e' `u' 2 "plain `scenario'"
    assert _N == 25
    assert id == _n
    assert missing(answer)
    a6_log "plain-`scenario'.smcl" "plain `scenario'"
}
// Also compare all native-imported variable names and values.
save "actual.dta", replace
import delimited using "plain both/Problems_WIDE.csv", clear ///
    varnames(1) encoding("utf-8") bindquotes(strict)
cf _all using "actual.dta", all
