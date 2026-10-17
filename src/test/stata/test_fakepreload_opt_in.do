version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_fakepreload_opt_in" "double"
    exit
}
do "$CTOSIM_TESTDIR/fakepreload-helpers.do"
foreach extension in xml xlsx {
    a5_case none 2
    quietly log using "baseline-`extension'.log", text replace name(a5)
    ctosim using "input files/survey.`extension'", saving("baseline `extension'") clear
    local N = r(N)
    local warnings = r(warnings)
    quietly log close a5
    assert `N' == 3
    assert `warnings' == 2
    assert _N == 3
    assert id == _n
    a5_flag 0
    a5_log "baseline-`extension'.log" "baseline `extension'"
}
// Enabling it once must not enable it for subsequent invocations.
foreach extension in xml xlsx {
    a5_case one
    ctosim using "input files/survey.`extension'", saving("enabled `extension'") fakepreload clear
    a5_flag 1
    a5_case none
    ctosim using "input files/survey.`extension'", saving("disabled again `extension'") clear
    a5_flag 0
}
