version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_injected_problems_simulator_failures" "double"
    exit
}
do "$CTOSIM_TESTDIR/injected-problems-helpers.do"
// Even a stale report/key must not be announced after an unsuccessful simulation.
a8_case 2
ctosim using "input files/survey.xml", problems saving("old run") clear
foreach status in 2 1 17 {
    ct_memory
    a8_case 2 1 `status'
    local original `"`c(pwd)'"'
    quietly log using "failure-`status'.log", text replace name(a8)
    capture noisily ctosim using "input files/survey.xml", problems saving("old run") replace clear
    local rc = _rc
    quietly log close a8
    if `status' == 2 assert `rc' == 198
    else assert `rc' == 498
    ct_invoked
    ct_memory_unchanged
    assert `"`c(pwd)'"' == `"`original'"'
    ct_contains "failure-`status'.log" "SIMULATOR_FAILURE_11_STATUS_`status'"
    a8_log "failure-`status'.log" -1 0
}
