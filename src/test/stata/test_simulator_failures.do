version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_simulator_failures" "double"
    exit
}

do "$CTOSIM_TESTDIR/helpers.do"
foreach status in 2 1 17 255 {
    ct_scenario `status'
    ct_memory
    local original `"`c(pwd)'"'
    quietly log using "failure`status'.log", text replace name(capture11)
    capture noisily ctosim using "input files/survey.xml", ///
        saving("failure `status'") dofile("$CTOSIM_TESTDIR/fixtures/import_failure.do") clear
    local rc = _rc
    quietly log close capture11
    if `status' == 2 assert `rc' == 198
    else assert `rc' == 498
    ct_invoked
    ct_memory_unchanged
    assert `"`c(pwd)'"' == `"`original'"'
    ct_contains "failure`status'.log" "SIMULATOR_STDOUT_11"
    ct_contains "failure`status'.log" "SIMULATOR_STDERR_11"
    ct_contains "failure`status'.log" "SIMULATOR_FAILURE_11_STATUS_`status'"
    capture confirm variable template_partial
    assert _rc != 0
}

// A saved dataset must also survive a failed run without acquiring changed status.
use "$CT_TEST_WORK/saved_memory.dta", clear
ct_scenario 1
capture noisily ctosim using "input files/survey.xml", saving("saved failure")
local rc = _rc
assert `rc' == 498
assert c(changed) == 0
cf _all using "$CT_TEST_WORK/saved_memory.dta", all

// A failed replacement must not import a previous successful run's stale CSV.
ct_scenario 0
ctosim using "input files/survey.xml", saving("stale run") clear
ct_memory
ct_scenario 1
capture noisily ctosim using "input files/survey.xml", saving("stale run") replace clear
local rc = _rc
assert `rc' == 498
ct_memory_unchanged
