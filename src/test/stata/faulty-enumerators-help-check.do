version 16.0
args area
findfile ctosim.sthlp
local helpfile "`r(fn)'"
adopath ++ "$CTOSIM_REPO/.codex-runs/stata-support"
local diagnostic "$CTOSIM_REPO/.codex-runs/stata-support/faulty-enumerators-help-`area'.failure.txt"
capture erase "`diagnostic'"
capture noisily javacall FaultyEnumeratorsHelpChecks help, jars(faulty-enumerators-help.jar) ///
    args("`helpfile'" "`area'" "`diagnostic'")
local rc = _rc
if `rc' {
    capture noisily type "`diagnostic'"
    error `rc'
}
