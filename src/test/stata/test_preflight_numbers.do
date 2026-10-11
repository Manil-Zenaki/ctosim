version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_preflight_numbers" "double"
    exit
}
do "$CTOSIM_TESTDIR/helpers.do"
ct_memory
foreach bad in 0 -1 1000001 1.5 . {
    ct_scenario 0
    capture noisily ctosim using "input files/survey.xml", n(`bad') clear saving("invalid")
    local rc = _rc
    assert `rc' == 198
    ct_not_invoked
    ct_memory_unchanged
}
foreach bad in 1.5 -2.75 . {
    ct_scenario 0
    capture noisily ctosim using "input files/survey.xml", seed(`bad') clear saving("invalid")
    local rc = _rc
    assert `rc' == 198
    ct_not_invoked
    ct_memory_unchanged
}
// Malformed tokens may use syntax's own error code, but must fail preflight.
foreach option in n seed {
    capture noisily ctosim using "input files/survey.xml", `option'(potato) clear
    local rc = _rc
    assert `rc' != 0
    ct_not_invoked
    capture noisily ctosim using "input files/survey.xml", `option'() clear
    local rc = _rc
    assert `rc' != 0
    ct_not_invoked
}
// Exercise inclusive bounds without simulating a million rows: launch must occur.
foreach valid in 1 1000000 {
    ct_scenario 1
    capture noisily ctosim using "input files/survey.xml", n(`valid') clear saving("valid")
    local rc = _rc
    assert `rc' == 498
    ct_invoked
    ct_argument "-n" "`valid'"
    ct_memory_unchanged
}
foreach valid in -2147483649 -1 0 2147483648 {
    ct_scenario 1
    capture noisily ctosim using "input files/survey.xml", seed(`valid') clear saving("valid")
    local rc = _rc
    assert `rc' == 498
    ct_invoked
    ct_argument "-s" "`valid'"
}
