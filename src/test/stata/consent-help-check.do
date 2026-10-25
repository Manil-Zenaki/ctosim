version 16.0
args area
findfile ctosim.sthlp
local helpfile "`r(fn)'"
adopath ++ "$CTOSIM_REPO/.codex-runs/stata-support"
local diagnostic "$CTOSIM_REPO/.codex-runs/stata-support/consent-help-`area'.failure.txt"
capture erase "`diagnostic'"
capture noisily javacall ConsentHelpChecks help, jars(consent-help.jar) ///
    args("`helpfile'" "`area'" "`diagnostic'")
local rc = _rc
if `rc' {
    capture noisily type "`diagnostic'"
    error `rc'
}
