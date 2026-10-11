version 16.0
args child
if "`child'" != "child" {
    local failures 0
    foreach profile in override_8 override_11 override_16 override_missing override_empty override_unset {
        capture noisily do "$CTOSIM_TESTDIR/amendment-launch.do" "test_amendment_java_stata_fallback" "`profile'"
        if _rc local ++failures
    }
    assert `failures' == 0
    exit
}
do "$CTOSIM_TESTDIR/amendment-helpers.do"
// Reject old/missing overrides; prefer eligible Stata Java over HOME and PATH.
// On Stata with Java <17, continue to HOME instead of executing the old JVM.
ct_amendment_selection
