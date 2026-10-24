version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_injected_problems_absent" "double"
    exit
}
do "$CTOSIM_TESTDIR/injected-problems-helpers.do"
a8_case -1
ctosim using "input files/survey.xml", saving("initial clean") clear
a8_results -1 0 "initial clean"
a8_flag "--problems" 0
capture confirm file "initial clean/answer-key.csv"
assert _rc == 601
a8_case 2
ctosim using "input files/survey.xml", problems saving("reused output") clear
a8_results 2 0 "reused output"
// A stale file alone is not evidence that the current run injected problems.
a8_case -1 1
quietly log using "absent.log", text replace name(a8)
ctosim using "input files/survey.xml", saving("reused output") replace clear
a8_results -1 1 "reused output"
quietly log close a8
a8_flag "--problems" 0
a8_log "absent.log" -1 1
