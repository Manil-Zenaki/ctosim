version 16.0
args test profile
adopath ++ "$CTOSIM_REPO/.codex-runs/stata-support"
capture noisily javacall ConverterNameTestSupport launch, jars(converter-name-test-support.jar) ///
    args("$CTOSIM_REPO" "`test'" "`profile'")
local rc = _rc
if `rc' {
    display as error "See .codex-runs/stata-converter-name/`test'-`profile'.log"
    error `rc'
}
