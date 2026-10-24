version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_injected_problems_display" "double"
    exit
}
do "$CTOSIM_TESTDIR/injected-problems-helpers.do"
foreach warnings in 0 2 {
    foreach count in 0 2 {
        a8_case `count' `warnings'
        local warninglabel = cond(`warnings' == 0, "quiet", "warnings")
        local countlabel = cond(`count' == 0, "empty", "several")
        quietly log using "display-`warnings'-`count'.log", text replace name(a8)
        ctosim using "input files/survey.xml", problems saving("`warninglabel' `countlabel'") clear
        quietly log close a8
        a8_log "display-`warnings'-`count'.log" `count' `warnings'
    }
}
