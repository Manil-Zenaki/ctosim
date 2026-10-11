version 16.0
args child
if "`child'" != "child" {
    local failures 0
    foreach profile in none_old none_missing none_empty {
        capture noisily do "$CTOSIM_TESTDIR/amendment-launch.do" "test_amendment_java_unavailable" "`profile'"
        if _rc local ++failures
    }
    assert `failures' == 0
    exit
}
do "$CTOSIM_TESTDIR/amendment-helpers.do"
ct_amendment_version
local own = r(major)
if `own' >= 17 {
    // Cannot make this installed JVM unavailable without changing the installation.
    // Still check that unusable alternatives do not prevent its selection.
    display "No-Java negative case inapplicable: Stata Java is `own'; checking Stata fallback."
    global CT_AMENDMENT_EXPECT "own"
    ct_amendment_selection
    exit
}
ct_scenario 0
ct_memory
local original `"`c(pwd)'"'
quietly log using "missing-java.log", text replace name(amendment)
capture noisily ctosim using "input files/survey.xml", saving("must not simulate") ///
    dofile("$CTOSIM_TESTDIR/fixtures/import_failure.do") clear
local rc = _rc
quietly log close amendment
assert `rc' == 601
ct_not_invoked
ct_amendment_selected "none"
ct_memory_unchanged
assert `"`c(pwd)'"' == `"`original'"'
capture confirm file "must not simulate/run-report.txt"
assert _rc == 601
javacall AmendmentTestSupport diagnostic, jars(amendment-test-support.jar) ///
    args("$CT_TEST_WORK/missing-java.log" "`own'")
