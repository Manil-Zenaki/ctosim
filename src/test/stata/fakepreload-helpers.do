version 16.0
do "$CTOSIM_TESTDIR/helpers.do"
// The launcher supplies a private disposable jar, never the built simulator.
local simulator : environment CTOSIM_JAR
copy "$CTOSIM_REPO/.codex-runs/stata-support/fakepreload-fixture.jar" "`simulator'", replace

program define a5_case
    args mode warnings unsatisfied status
    if "`warnings'" == "" local warnings 0
    if "`unsatisfied'" == "" local unsatisfied 0
    if "`status'" == "" local status 0
    tempname f
    file open `f' using "$CT_TEST_WORK/fakepreload-case.txt", write text replace
    file write `f' "`mode' `warnings' `unsatisfied' `status'" _n
    file close `f'
    capture erase "$CT_TEST_WORK/invoked.txt"
end

program define a5_flag
    args expected
    tempname f
    file open `f' using "$CT_TEST_WORK/arguments.txt", read text
    local found 0
    file read `f' line
    while r(eof) == 0 {
        if `"`macval(line)'"' == "--fake-attachments" local ++found
        file read `f' line
    }
    file close `f'
    assert `found' == `expected'
end

program define a5_results
    local fake `"`r(fakepreload)'"'
    local N = r(N)
    local seed = r(seed)
    local warnings = r(warnings)
    local unsatisfied = r(unsatisfied)
    local form `"`r(form_id)'"'
    local out `"`r(outdir)'"'
    local wide `"`r(wide)'"'
    local report `"`r(report)'"'
    args names warningcount fields output
    assert `"`fake'"' == `"`names'"'
    assert `N' == 3
    assert `seed' == -713
    assert `warnings' == `warningcount'
    assert `unsatisfied' == `fields'
    assert "`form'" == "preload_fixture"
    foreach path in out wide report {
        local norm = subinstr("``path''", "\", "/", .)
        assert regexm("`norm'", "^[A-Za-z]:/") | substr("`norm'", 1, 2) == "//"
    }
    javacall StataTestSupport samePaths, jars(stata-test-support.jar) ///
        args("`out'" "$CT_TEST_WORK/`output'" ///
        "`wide'" "$CT_TEST_WORK/`output'/Preload fixture_WIDE.csv" ///
        "`report'" "$CT_TEST_WORK/`output'/run-report.txt")
end

program define a5_log
    args logfile output
    capture noisily javacall FakepreloadFixture log, jars(fakepreload-fixture.jar) ///
        args("$CT_TEST_WORK/`logfile'" "$CT_TEST_WORK/`output'/run-report.txt")
    local rc = _rc
    if `rc' {
        capture noisily type "$CT_TEST_WORK/`logfile'.failure.txt"
        error `rc'
    }
end
