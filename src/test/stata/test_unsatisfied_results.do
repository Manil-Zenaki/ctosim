version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_unsatisfied_results" "double"
    exit
}
do "$CTOSIM_TESTDIR/unsatisfied-helpers.do"
// F is distinct from N, warning count, line count and number of occurrences.
foreach fields in 1 3 12 {
    a4_case `fields'
    ctosim using "input files/survey.xml", saving("results `fields'") clear
    local warnings = `fields' + 24
    a4_results `fields' `warnings' "results `fields'"
}
