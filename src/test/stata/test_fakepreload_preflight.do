version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_fakepreload_preflight" "double"
    exit
}
do "$CTOSIM_TESTDIR/fakepreload-helpers.do"
a5_case one
ct_memory
capture noisily ctosim using "input files/survey.xml", saving("protected memory") fakepreload
assert _rc == 4
ct_not_invoked
ct_memory_unchanged

// fakepreload permits missing attached files, not a missing FORM/config/import template.
foreach missing in form config dofile {
    a5_case one
    local form "input files/survey.xml"
    local extra ""
    if "`missing'" == "form" local form "input files/missing.xml"
    if "`missing'" == "config" local extra `"config("input files/missing.json")"'
    if "`missing'" == "dofile" local extra `"dofile("input files/missing.do")"'
    capture noisily ctosim using "`form'", saving("missing `missing'") fakepreload clear `extra'
    assert _rc == 601
    ct_not_invoked
    ct_memory_unchanged
}

// Authorizing fictitious media does not authorize overwriting an existing run.
a5_case one
ctosim using "input files/survey.xml", saving("existing run") fakepreload clear
ct_memory
a5_case one
capture noisily ctosim using "input files/survey.xml", saving("existing run") fakepreload clear
assert _rc == 602
ct_not_invoked
ct_memory_unchanged
ctosim using "input files/survey.xml", saving("existing run") fakepreload clear replace
a5_results "people.csv" 0 0 "existing run"
a5_flag 1
