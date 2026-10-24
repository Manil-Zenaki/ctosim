version 16.0
do "$CTOSIM_TESTDIR/helpers.do"
// Only the child launcher's disposable simulator is replaced.
local simulator : environment CTOSIM_JAR
copy "$CTOSIM_REPO/.codex-runs/stata-support/injected-problems-fixture.jar" "`simulator'", replace

program define a8_case
    args count warnings status
    if "`warnings'" == "" local warnings 0
    if "`status'" == "" local status 0
    ct_scenario `status' `warnings' 3 -713
    tempname f
    file open `f' using "$CT_TEST_WORK/injected-case.txt", write text replace
    file write `f' "`count'" _n
    file close `f'
end

program define a8_flag
    args flag expected
    tempname f
    file open `f' using "$CT_TEST_WORK/arguments.txt", read text
    local count 0
    file read `f' line
    while r(eof) == 0 {
        if `"`macval(line)'"' == "`flag'" local ++count
        file read `f' line
    }
    file close `f'
    assert `count' == `expected'
end

program define a8_results
    // Capture before any file operation or other r-class command.
    local count = r(problems)
    local key `"`r(answerkey)'"'
    local scalars : r(scalars)
    local macros : r(macros)
    args expected warnings output
    ct_report_results 3 -713 `warnings' "`output'"
    if `expected' < 0 {
        assert !strpos(" `scalars' ", " problems ")
        assert !strpos(" `macros' ", " answerkey ")
        assert missing(`count')
        assert `"`key'"' == ""
    }
    else {
        assert strpos(" `scalars' ", " problems ") > 0
        assert strpos(" `macros' ", " answerkey ") > 0
        assert `count' == `expected'
        local normalized = subinstr(`"`key'"', "\", "/", .)
        assert regexm(`"`normalized'"', "^[A-Za-z]:/") | substr(`"`normalized'"', 1, 2) == "//"
        confirm file `"`key'"'
        javacall StataTestSupport samePaths, jars(stata-test-support.jar) ///
            args(`"`key'"' "$CT_TEST_WORK/`output'/answer-key.csv")
    }
end

program define a8_log
    args logfile count warnings
    capture noisily javacall InjectedProblemsFixture log, jars(injected-problems-fixture.jar) ///
        args("$CT_TEST_WORK/`logfile'" "`count'" "`warnings'")
    local rc = _rc
    if `rc' {
        capture noisily type "$CT_TEST_WORK/`logfile'.failure.txt"
        error `rc'
    }
end
