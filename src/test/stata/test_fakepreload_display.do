version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_fakepreload_display" "double"
    exit
}
do "$CTOSIM_TESTDIR/fakepreload-helpers.do"
// The simulator already prints a warning count; that cannot stand in for step 3.
// Include absent lines, one/many names, zero/nonzero warnings, and the other report section.
foreach mode in none one many {
    foreach warnings in 0 2 {
        foreach unsatisfied in 0 1 {
            local case "`mode'-`warnings'-`unsatisfied'"
            a5_case `mode' `warnings' `unsatisfied'
            quietly log using "display-`case'.log", text replace name(a5)
            ctosim using "input files/survey.xml", saving("display `case'") fakepreload clear
            quietly log close a5
            a5_log "display-`case'.log" "display `case'"
            if `unsatisfied' == 1 ct_contains "display-`case'.log" "Unsatisfied constraints: 1"
        }
    }
}
