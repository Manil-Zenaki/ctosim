version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_form_problems_absent" "double"
    exit
}
do "$CTOSIM_TESTDIR/form-problems-helpers.do"
// Repeated calls must clear stale counts AND stale final diagnostics.
a6_case 12 3 31 1
ctosim using "input files/survey.xml", saving("transition") fakepreload clear
a6_results 12 3 31 "transition" "people.csv villages.csv"
foreach warnings in 0 2 {
    a6_case 0 0 `warnings' 1
    quietly log using "absent-`warnings'.smcl", smcl replace name(a6)
    ctosim using "input files/survey.xml", saving("transition") fakepreload clear replace
    a6_results 0 0 `warnings' "transition" "people.csv villages.csv"
    quietly log close a6
    a6_log "absent-`warnings'.smcl" "transition"
    assert _N == 25
}
// Absence of all optional sections, followed by a newly present single error.
a6_case 0 0 0 0
quietly log using "old-report.smcl", smcl replace name(a6)
ctosim using "input files/survey.xml", saving("transition") clear replace
a6_results 0 0 0 "transition"
quietly log close a6
a6_log "old-report.smcl" "transition"
a6_case 1 0 0 0
quietly log using "present-again.smcl", smcl replace name(a6)
ctosim using "input files/survey.xml", saving("transition") clear replace
a6_results 1 0 0 "transition"
quietly log close a6
a6_log "present-again.smcl" "transition"

