version 16.0
args area
findfile ctosim.sthlp
local helpfile "`r(fn)'"
adopath ++ "$CTOSIM_REPO/.codex-runs/stata-support"
capture noisily javacall HelpUseCasesTestSupport check, jars(help-use-cases-test-support.jar) ///
    args("$CTOSIM_REPO" "`area'" "`helpfile'")
local rc = _rc
if `rc' {
    capture noisily type "$CTOSIM_REPO/.codex-runs/stata-help-use-cases/`area'.log"
    error `rc'
}
