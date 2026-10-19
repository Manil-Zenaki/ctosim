version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_id_preflight" "double"
    exit
}
do "$CTOSIM_TESTDIR/id-helpers.do"
ct_scenario 0
ct_memory
capture noisily ctosim using "input files/survey.xml", id(quantity) saving("protected")
assert _rc == 4
ct_not_invoked
ct_memory_unchanged
ctosim using "input files/survey.xml", id(quantity) saving("existing") clear
ct_memory
ct_scenario 0
capture noisily ctosim using "input files/survey.xml", id(quantity) saving("existing") clear
assert _rc == 602
ct_not_invoked
ct_memory_unchanged
ctosim using "input files/survey.xml", id(quantity) saving("existing") clear replace
a7_id_argument "quantity"

foreach missing in form config dofile {
    ct_scenario 0
    ct_memory
    local form "input files/survey.xml"
    local extra ""
    if "`missing'" == "form" local form "input files/missing.xml"
    if "`missing'" == "config" local extra `"config("input files/missing.json")"'
    if "`missing'" == "dofile" local extra `"dofile("input files/missing.do")"'
    capture noisily ctosim using "`form'", id(quantity) saving("missing `missing'") clear `extra'
    assert _rc == 601
    ct_not_invoked
    ct_memory_unchanged
}
