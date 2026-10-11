version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/amendment-launch.do" "test_amendment_batch_failures" "batch"
    exit
}
do "$CTOSIM_TESTDIR/amendment-helpers.do"
foreach status in 2 1 17 255 {
    ct_scenario `status'
    ct_memory
    capture erase "$CT_TEST_WORK/launched-override.txt"
    local original `"`c(pwd)'"'
    quietly log using "batch-error-`status'.log", text replace name(amendment)
    capture noisily ctosim using "input files/survey.xml", saving("batch error `status'") ///
        dofile("$CTOSIM_TESTDIR/fixtures/import_failure.do") clear
    local rc = _rc
    quietly log close amendment
    if `status' == 2 assert `rc' == 198
    else assert `rc' == 498
    ct_invoked
    ct_amendment_selected "override"
    ct_memory_unchanged
    assert `"`c(pwd)'"' == `"`original'"'
    ct_contains "batch-error-`status'.log" "SIMULATOR_STDOUT_11"
    ct_contains "batch-error-`status'.log" "SIMULATOR_STDERR_11"
    ct_contains "batch-error-`status'.log" "SIMULATOR_FAILURE_11_STATUS_`status'"
}

// After successful simulation, a template error must retain its own Stata code
// and partial data, and restore cwd, even when running noninteractively.
ct_scenario 0
ct_memory
capture erase "$CT_TEST_WORK/launched-override.txt"
local original `"`c(pwd)'"'
capture noisily ctosim using "input files/survey.xml", saving("batch template failure") ///
    dofile("$CTOSIM_TESTDIR/fixtures/import_failure.do") clear
local rc = _rc
assert `rc' == 459
ct_invoked
ct_amendment_selected "override"
assert _N == 1
assert template_partial == 73
assert `"`c(pwd)'"' == `"`original'"'
