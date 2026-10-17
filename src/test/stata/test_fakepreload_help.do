version 16.0
findfile ctosim.sthlp
local helpfile "`r(fn)'"
adopath ++ "$CTOSIM_REPO/.codex-runs/stata-support"
local diagnostic "$CTOSIM_REPO/.codex-runs/stata-support/fakepreload-help.failure.txt"
capture noisily javacall FakepreloadFixture help, jars(fakepreload-fixture.jar) ///
    args("`helpfile'" "`diagnostic'")
local rc = _rc
if `rc' {
    capture noisily type "`diagnostic'"
    error `rc'
}
