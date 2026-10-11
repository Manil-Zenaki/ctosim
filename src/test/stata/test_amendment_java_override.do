version 16.0
args child
if "`child'" != "child" {
    local failures 0
    // Exact minimum, newer releases, stderr and stdout version reporting.
    foreach profile in override_17 override_21_stdout override_25 {
        capture noisily do "$CTOSIM_TESTDIR/amendment-launch.do" "test_amendment_java_override" "`profile'"
        if _rc local ++failures
    }
    assert `failures' == 0
    exit
}
do "$CTOSIM_TESTDIR/amendment-helpers.do"
// A valid override wins even when Stata, JAVA_HOME and PATH have newer Java.
ct_amendment_selection
