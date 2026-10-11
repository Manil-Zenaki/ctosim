version 16.0
args child
if "`child'" != "child" {
    local failures 0
    foreach profile in home_17 home_16 home_missing home_empty home_unset path_17 {
        capture noisily do "$CTOSIM_TESTDIR/amendment-launch.do" "test_amendment_java_home_path" "`profile'"
        if _rc local ++failures
    }
    assert `failures' == 0
    exit
}
do "$CTOSIM_TESTDIR/amendment-helpers.do"
// JAVA_HOME/bin/java takes precedence over PATH; old/missing/empty/unset
// HOME falls through. An eligible Stata Java continues to precede both.
ct_amendment_selection
