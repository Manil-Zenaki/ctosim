version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_form_problems_help" "double"
    exit
}
do "$CTOSIM_TESTDIR/form-problems-helpers.do"
// Inspect only the public help artifact, with a spec-derived semantic smoke check.
findfile ctosim.sthlp
local help "`r(fn)'"
capture noisily javacall FormProblemsFixture help, jars(form-problems-fixture.jar) ///
    args("`help'" "$CT_TEST_WORK/a6-help-failure.txt")
local rc = _rc
if `rc' {
    capture noisily type "$CT_TEST_WORK/a6-help-failure.txt"
    error `rc'
}

