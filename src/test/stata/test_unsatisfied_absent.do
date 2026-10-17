version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_unsatisfied_absent" "double"
    exit
}
do "$CTOSIM_TESTDIR/unsatisfied-helpers.do"
// Older reports have no section, with either zero or unrelated warnings.
foreach mode in 0 -1 {
    a4_case `mode'
    quietly log using "absent`mode'.smcl", smcl replace name(a4)
    ctosim using "input files/survey.xml", saving("absent `mode'") clear
    local warnings = cond(`mode' == 0, 0, 2)
    a4_results 0 `warnings' "absent `mode'"
    quietly log close a4
    a4_log "absent`mode'.smcl" "absent `mode'"
}
// A previous nonzero result cannot leak into the next no-section run.
a4_case 12
ctosim using "input files/survey.xml", saving("transition") clear
assert r(unsatisfied) == 12
a4_case 0
ctosim using "input files/survey.xml", saving("transition") clear replace
a4_results 0 0 "transition"
// And a previous zero must not suppress a subsequent section.
a4_case 1
ctosim using "input files/survey.xml", saving("transition") clear replace
a4_results 1 25 "transition"
