version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_id_real_warnings" "real"
    exit
}
do "$CTOSIM_TESTDIR/id-helpers.do"
a7_plan_fixture
// A single word naming no question is a plan warning, not a command syntax error.
quietly log using "unknown-id.log", text replace name(idunknown)
ctosim using "input files/plan.xml", id(unknown_question) n(5) saving("unknown id") clear
local report `"`r(report)'"'
assert r(N) == 5
assert r(warnings) == 1
quietly log close idunknown
assert _N == 5
ct_contains `"`report'"' "unknown_question"
a7_warning_display `"`report'"' "unknown-id.log"

tempname f
file open `f' using "input files/two-values.json", write text replace
file write `f' `"{"submissions":5,"missingRate":0,"integerRange":{"min":71,"max":72}}"' _n
file close `f'
// Spec 13 qualifies distinctness when the possible id pool is exhausted.
quietly log using "exhausted-id.log", text replace name(idexhausted)
ctosim using "input files/plan.xml", config("input files/two-values.json") ///
    id(survey_id) saving("exhausted id") clear
local report `"`r(report)'"'
assert r(N) == 5
assert r(warnings) == 1
quietly log close idexhausted
assert _N == 5
assert inlist(survey_id, 71, 72)
quietly count if survey_id == 71
assert inlist(r(N), 2, 3)
ct_contains `"`report'"' "survey_id"
a7_warning_display `"`report'"' "exhausted-id.log"
