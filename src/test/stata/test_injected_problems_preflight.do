version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_injected_problems_preflight" "double"
    exit
}
do "$CTOSIM_TESTDIR/injected-problems-helpers.do"
a8_case 2
ct_memory
capture noisily ctosim using "input files/survey.xml", problems saving("protected")
assert _rc == 4
ct_not_invoked
ct_memory_unchanged
foreach option in "n(0)" "n(1000001)" "n(1.5)" "seed(1.5)" "id(two words)" {
    a8_case 2
    capture noisily ctosim using "input files/survey.xml", problems `option' saving("invalid") clear
    assert _rc == 198
    ct_not_invoked
    ct_memory_unchanged
}
foreach option in "config(absent.json)" "dofile(absent.do)" {
    a8_case 2
    capture noisily ctosim using "input files/survey.xml", problems `option' saving("invalid") clear
    assert _rc == 601
    ct_not_invoked
    ct_memory_unchanged
}
a8_case 2
capture noisily ctosim using "input files/absent.xml", problems saving("invalid") clear
assert _rc == 601
ct_not_invoked
ct_memory_unchanged
// Existing-run guard, then successful explicitly requested replacement.
ctosim using "input files/survey.xml", problems saving("existing") clear
ct_memory
a8_case 1
capture noisily ctosim using "input files/survey.xml", problems saving("existing") clear
assert _rc == 602
ct_not_invoked
ct_memory_unchanged
ctosim using "input files/survey.xml", problems saving("existing") replace clear
a8_results 1 0 "existing"
