version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_preflight_files" "double"
    exit
}
do "$CTOSIM_TESTDIR/helpers.do"
ct_scenario 0
ct_memory
capture noisily ctosim using "missing form.xml", saving("not created") clear
local rc = _rc
assert `rc' == 601
ct_not_invoked
ct_memory_unchanged

foreach extension in txt csv xls xml.bak {
    copy "input files/survey.xml" "input files/invalid.`extension'", replace
    capture noisily ctosim using "input files/invalid.`extension'", saving("not created") clear
    local rc = _rc
    assert `rc' == 198
    ct_not_invoked
    ct_memory_unchanged
}
copy "input files/survey.xml" "input files/noextension", replace
capture noisily ctosim using "input files/noextension", saving("not created") clear
local rc = _rc
assert `rc' == 198
ct_not_invoked
foreach option in config dofile {
    capture noisily ctosim using "input files/survey.xml", `option'("missing file.txt") saving("not created") clear
    local rc = _rc
    assert `rc' == 601
    ct_not_invoked
    ct_memory_unchanged
}
capture confirm file "not created/run-report.txt"
assert _rc == 601
local created : dir "." dirs "not created"
assert `"`created'"' == ""
