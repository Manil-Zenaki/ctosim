version 16.0
do "$CTOSIM_TESTDIR/helpers.do"
// launch.do supplies a private disposable simulator, never the built package jar.
local simulator : environment CTOSIM_JAR
copy "$CTOSIM_REPO/.codex-runs/stata-support/form-problems-fixture.jar" "`simulator'", replace

program define a6_case
    args errors unsatisfied warnings fake status
    if "`status'" == "" local status 0
    tempname f
    file open `f' using "$CT_TEST_WORK/form-problems-case.txt", write text replace
    file write `f' "`errors' `unsatisfied' `warnings' `fake' `status'" _n
    file close `f'
end

program define a6_results
    local errors = r(formerrors)
    local unsatisfied = r(unsatisfied)
    local N = r(N)
    local warnings = r(warnings)
    local seed = r(seed)
    local form "`r(form_id)'"
    local out "`r(outdir)'"
    local wide "`r(wide)'"
    local report "`r(report)'"
    local fake "`r(fakepreload)'"
    args e u w output names
    assert `errors' == `e'
    assert `unsatisfied' == `u'
    assert `N' == 25
    assert `warnings' == `w'
    assert `seed' == -713
    assert "`form'" == "form_problems_fixture"
    // No contract for r(fakepreload) when the option is omitted.
    if "`names'" != "" assert "`fake'" == "`names'"
    foreach path in out wide report {
        local norm = subinstr("``path''", "\", "/", .)
        assert regexm("`norm'", "^[A-Za-z]:/") | substr("`norm'", 1, 2) == "//"
    }
    javacall StataTestSupport samePaths, jars(stata-test-support.jar) ///
        args("`out'" "$CT_TEST_WORK/`output'" ///
             "`wide'" "$CT_TEST_WORK/`output'/Problems_WIDE.csv" ///
             "`report'" "$CT_TEST_WORK/`output'/run-report.txt")
end

program define a6_log
    args logfile output mode code
    if "`mode'" == "" local mode full
    if "`code'" == "" local code 0
    capture noisily javacall FormProblemsFixture log, jars(form-problems-fixture.jar) ///
        args("$CT_TEST_WORK/`logfile'" "$CT_TEST_WORK/`output'/run-report.txt" "`mode'" "`code'")
    local rc = _rc
    if `rc' {
        capture noisily type "$CT_TEST_WORK/`logfile'.failure.txt"
        error `rc'
    }
end
