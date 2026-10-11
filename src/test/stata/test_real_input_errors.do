version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_real_input_errors" "real_configured_python"
    exit
}
do "$CTOSIM_TESTDIR/helpers.do"
ct_memory
// Missing catalog is a simulator input failure, not a missing FORM preflight.
copy "input files/survey.xml" "input files/no catalog.xml", replace
capture noisily ctosim using "input files/no catalog.xml", saving("no catalog") clear
local rc = _rc
assert `rc' == 498
ct_memory_unchanged

tempname f
file open `f' using "invalid.json", write text replace
file write `f' "not JSON" _n
file close `f'
capture noisily ctosim using "input files/survey.xml", config("invalid.json") saving("bad config") clear
local rc = _rc
assert `rc' == 498
ct_memory_unchanged
capture noisily ctosim using "input files/survey.xml", media("missing media directory") saving("bad media") clear
local rc = _rc
assert `rc' == 498
ct_memory_unchanged

// Both malformed input kinds exist, so validation proceeds to the simulator.
copy "invalid.json" "input files/invalid.xml", replace
copy "input files/survey.fields.json" "input files/invalid.fields.json", replace
copy "invalid.json" "input files/invalid.xlsx", replace
foreach extension in xml xlsx {
    capture noisily ctosim using "input files/invalid.`extension'", saving("bad `extension'") clear
    local rc = _rc
    assert `rc' == 498
    ct_memory_unchanged
}
