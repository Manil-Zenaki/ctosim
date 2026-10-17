version 16.0
findfile ctosim.sthlp
local helpfile "`r(fn)'"
adopath ++ "$CTOSIM_REPO/.codex-runs/stata-support"
capture mkdir "$CTOSIM_REPO/.codex-runs/stata-unsatisfied"
local diagnostic "$CTOSIM_REPO/.codex-runs/stata-unsatisfied/help.failure.txt"
capture noisily javacall UnsatisfiedFixture help, jars(unsatisfied-fixture.jar) ///
    args("`helpfile'" "`diagnostic'")
local rc = _rc
if `rc' {
    capture noisily type "`diagnostic'"
    error `rc'
}
