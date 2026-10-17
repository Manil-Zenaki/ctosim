version 16.0
// Existing isolation creates a private simulator jar; replace only that disposable jar.
do "$CTOSIM_TESTDIR/helpers.do"
local simulator : environment CTOSIM_JAR
copy "$CTOSIM_REPO/.codex-runs/stata-support/unsatisfied-fixture.jar" "`simulator'", replace

program define a4_case
    args fields
    tempname f
    file open `f' using "$CT_TEST_WORK/unsatisfied-case.txt", write text replace
    file write `f' "`fields'" _n
    file close `f'
end

program define a4_log
    args log output
    capture noisily javacall UnsatisfiedFixture log, jars(unsatisfied-fixture.jar) ///
        args("$CT_TEST_WORK/`log'" "$CT_TEST_WORK/`output'/run-report.txt")
    local rc = _rc
    if `rc' {
        capture noisily type "$CT_TEST_WORK/`log'.failure.txt"
        error `rc'
    }
end

program define a4_results
    // Capture everything before assertions or file operations can overwrite r().
    local unsatisfied = r(unsatisfied)
    local N = r(N)
    local warnings = r(warnings)
    local seed = r(seed)
    local form "`r(form_id)'"
    local out "`r(outdir)'"
    local wide "`r(wide)'"
    local report "`r(report)'"
    args fields warningcount output
    assert `unsatisfied' == `fields'
    assert `N' == 25
    assert `warnings' == `warningcount'
    assert `seed' == -713
    assert "`form'" == "unsatisfied_fixture"
    foreach path in out wide report {
        local norm = subinstr("``path''", "\", "/", .)
        assert regexm("`norm'", "^[A-Za-z]:/") | substr("`norm'", 1, 2) == "//"
    }
    javacall StataTestSupport samePaths, jars(stata-test-support.jar) ///
        args("`out'" "$CT_TEST_WORK/`output'" ///
             "`wide'" "$CT_TEST_WORK/`output'/Constraint fixture_WIDE.csv" ///
             "`report'" "$CT_TEST_WORK/`output'/run-report.txt")
end
