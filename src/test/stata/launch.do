version 16.0
// Parent-side isolation. The child executes the same test with argument child.
args test profile
local root = subinstr("$CTOSIM_REPO", "\", "/", .)
capture mkdir "`root'/.codex-runs"
capture mkdir "`root'/.codex-runs/stata-command"
local result "`root'/.codex-runs/stata-command/`test'.passed"
capture erase "`result'"
// Windows shell/winexec are disabled in batch mode. Use a tiny Java bridge.
local failure "`root'/.codex-runs/stata-command/`test'.failure.txt"
capture erase "`failure'"
adopath ++ "$CTOSIM_REPO/.codex-runs/stata-support"
javacall StataTestSupport launch, jars(stata-test-support.jar) args("$CTOSIM_REPO" "`test'" "`profile'")
capture confirm file "`result'"
if _rc {
    capture type "`root'/.codex-runs/stata-command/`test'.failure.txt"
    display as error "Child test failed; see .codex-runs/stata-command/`test'/child.log"
    error 9
}
