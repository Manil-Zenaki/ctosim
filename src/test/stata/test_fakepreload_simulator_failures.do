version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_fakepreload_simulator_failures" "double"
    exit
}
do "$CTOSIM_TESTDIR/fakepreload-helpers.do"
foreach status in 1 2 17 {
    a5_case none 0 0 `status'
    ct_memory
    local original `"`c(pwd)'"'
    quietly log using "failure-`status'.log", text replace name(a5)
    capture noisily ctosim using "input files/survey.xml", ///
        saving("failure `status'") fakepreload clear
    local rc = _rc
    quietly log close a5
    assert `rc' == cond(`status' == 2, 198, 498)
    ct_memory_unchanged
    assert `"`c(pwd)'"' == `"`original'"'
    a5_flag 1
    ct_contains "failure-`status'.log" "A5_SIMULATOR_ERROR_`status'"
}
