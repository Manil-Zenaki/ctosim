version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_id_failures" "double"
    exit
}
do "$CTOSIM_TESTDIR/id-helpers.do"
foreach status in 2 1 17 {
    ct_scenario `status'
    ct_memory
    local original `"`c(pwd)'"'
    quietly log using "id-failure-`status'.log", text replace name(idfailure)
    capture noisily ctosim using "input files/survey.xml", id(quantity) ///
        saving("failure `status'") dofile("$CTOSIM_TESTDIR/fixtures/import_failure.do") clear
    local rc = _rc
    quietly log close idfailure
    if `status' == 2 assert `rc' == 198
    else assert `rc' == 498
    ct_invoked
    a7_id_argument "quantity"
    ct_memory_unchanged
    assert `"`c(pwd)'"' == `"`original'"'
    ct_contains "id-failure-`status'.log" "SIMULATOR_FAILURE_11_STATUS_`status'"
}
