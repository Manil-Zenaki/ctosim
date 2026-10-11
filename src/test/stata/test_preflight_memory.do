version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_preflight_memory" "double"
    exit
}
do "$CTOSIM_TESTDIR/helpers.do"
ct_scenario 0
ct_memory
capture noisily ctosim using "input files/survey.xml", saving("blocked")
local rc = _rc
assert `rc' == 4
ct_not_invoked
ct_memory_unchanged
capture confirm file "blocked/run-report.txt"
assert _rc == 601

// clear authorizes replacing changed data.
ctosim using "input files/survey.xml", saving("allowed") clear
assert _N == 3
capture confirm variable keeper
assert _rc != 0

// c(changed), rather than the observation count, determines protection.
clear
set obs 1
generate byte empty_schema = 1
drop in 1
assert _N == 0
assert c(changed) == 1
ct_scenario 0
capture noisily ctosim using "input files/survey.xml", saving("empty but changed")
local rc = _rc
assert `rc' == 4
ct_not_invoked
assert _N == 0
confirm variable empty_schema

// A saved, unchanged, nonempty dataset does not need clear.
use "$CT_TEST_WORK/saved_memory.dta", clear
assert c(changed) == 0
ctosim using "input files/survey.xml", saving("saved input")
assert _N == 3
