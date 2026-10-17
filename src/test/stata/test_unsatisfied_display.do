version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_unsatisfied_display" "double"
    exit
}
do "$CTOSIM_TESTDIR/unsatisfied-helpers.do"
// Each line, including optional references and the header, is red and precedes warnings.
foreach fields in 1 3 12 {
    a4_case `fields'
    quietly log using "display-`fields'.smcl", smcl replace name(a4)
    ctosim using "input files/survey.xml", saving("display `fields'") clear
    quietly log close a4
    a4_log "display-`fields'.smcl" "display `fields'"
}
