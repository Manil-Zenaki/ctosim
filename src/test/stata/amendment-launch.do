version 16.0
args test profile
adopath ++ "$CTOSIM_TESTDIR/fixtures"
capture noisily javacall AmendmentTestSupport launch, jars(amendment-test-support.jar) ///
    args("$CTOSIM_REPO" "`test'" "`profile'")
local rc = _rc
if `rc' {
    display as error "See .codex-runs/stata-amendment/`test'-`profile'.log for the child failures."
    error `rc'
}
