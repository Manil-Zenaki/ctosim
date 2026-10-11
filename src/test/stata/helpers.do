version 16.0
set more off
set linesize 255
clear all
adopath ++ "$CTOSIM_TESTDIR/fixtures"

program define ct_fixture
    capture mkdir "input files"
    copy "$CTOSIM_TESTDIR/fixtures/form.xml" "input files/survey.xml", replace
    copy "$CTOSIM_TESTDIR/fixtures/form.fields.json" "input files/survey.fields.json", replace
    copy "$CTOSIM_TESTDIR/fixtures/form.xlsx" "input files/survey.xlsx", replace
end

program define ct_scenario
    args status warnings n seed
    if "`warnings'" == "" local warnings 0
    tempname f
    file open `f' using "$CT_TEST_WORK/scenario.txt", write text replace
    file write `f' "exit=`status'" _n "warnings=`warnings'" _n
    if "`n'" != "" file write `f' "report_n=`n'" _n
    if "`seed'" != "" file write `f' "report_seed=`seed'" _n
    file close `f'
    capture erase "$CT_TEST_WORK/invoked.txt"
end

program define ct_not_invoked
    capture confirm file "$CT_TEST_WORK/invoked.txt"
    assert _rc == 601
end

program define ct_invoked
    confirm file "$CT_TEST_WORK/invoked.txt"
end

program define ct_memory
    clear
    set obs 2
    generate double keeper = _n + .25
    generate str20 text = "unchanged"
    label variable keeper "Original variable label"
    label data "Original data label"
    format keeper %12.2f
    char _dta[test11] "original characteristic"
    save "$CT_TEST_WORK/saved_memory.dta", replace
    replace keeper = keeper + 10
    assert c(changed) == 1
end

program define ct_memory_unchanged
    assert _N == 2
    quietly describe
    assert r(k) == 2
    assert keeper == _n + 10.25
    assert text == "unchanged"
    local label : variable label keeper
    assert "`label'" == "Original variable label"
    local label : data label
    assert "`label'" == "Original data label"
    local fmt : format keeper
    assert "`fmt'" == "%12.2f"
    local ch : char _dta[test11]
    assert "`ch'" == "original characteristic"
    assert c(changed) == 1
end

program define ct_contains
    // Exact substring within a text line; insensitive to log indentation.
    args path needle
    tempname f
    file open `f' using "`path'", read text
    local found 0
    file read `f' line
    while r(eof) == 0 {
        if strpos(`"`macval(line)'"', `"`macval(needle)'"') local found 1
        file read `f' line
    }
    file close `f'
    assert `found' == 1
end

program define ct_absent
    args path needle
    tempname f
    file open `f' using "`path'", read text
    file read `f' line
    while r(eof) == 0 {
        assert strpos(`"`macval(line)'"', `"`macval(needle)'"') == 0
        file read `f' line
    }
    file close `f'
end

program define ct_argument
    // An option and its value must be separate process arguments, without quotes.
    args option expected
    tempname f
    file open `f' using "$CT_TEST_WORK/arguments.txt", read text
    local found 0
    file read `f' line
    while r(eof) == 0 {
        if `"`macval(line)'"' == `"`option'"' {
            file read `f' value
            local value = subinstr(`"`macval(value)'"', "\", "/", .)
            local expected = subinstr(`"`macval(expected)'"', "\", "/", .)
            // Both relative and absolute resolutions of supplied paths are valid.
            assert (`"`macval(value)'"' == `"`expected'"') | ///
                (`"`macval(value)'"' == `"$CT_TEST_WORK/`expected'"')
            local ++found
        }
        file read `f' line
    }
    file close `f'
    assert `found' == 1
end

program define ct_report_results
    // Save all return values before file commands can overwrite r().
    local N = r(N)
    local warnings = r(warnings)
    local seed = r(seed)
    local form `"`r(form_id)'"'
    local out = subinstr(`"`r(outdir)'"', "\", "/", .)
    local wide = subinstr(`"`r(wide)'"', "\", "/", .)
    local report = subinstr(`"`r(report)'"', "\", "/", .)
    args expected_n expected_seed expected_warnings expected_dir
    assert `N' == `expected_n'
    assert `seed' == `expected_seed'
    assert `warnings' == `expected_warnings'
    assert `"`form'"' == "fixture_id_11"
    foreach path in out wide report {
        assert regexm(`"``path''"', "^[A-Za-z]:/") | substr(`"``path''"', 1, 2) == "//"
    }
    // Absolute paths need not use a particular slash style, case, or trailing slash.
    javacall StataTestSupport samePaths, jars(stata-test-support.jar) ///
        args(`"`out'"' "$CT_TEST_WORK/`expected_dir'" ///
             `"`wide'"' "$CT_TEST_WORK/`expected_dir'/Survey échantillon_WIDE.csv" ///
             `"`report'"' "$CT_TEST_WORK/`expected_dir'/run-report.txt")
    confirm file `"`wide'"'
    confirm file `"`report'"'
    ct_contains `"`report'"' "Seed: `expected_seed'"
    ct_contains `"`report'"' "Submissions: `expected_n'"
    ct_contains `"`report'"' "Warnings: `expected_warnings'"
end

program define ct_form_argument
    args expected
    tempname f
    file open `f' using "$CT_TEST_WORK/arguments.txt", read text
    local found 0
    file read `f' line
    while r(eof) == 0 {
        local value = subinstr(`"`macval(line)'"', "\", "/", .)
        if (`"`value'"' == `"`expected'"') | ///
            (`"`value'"' == `"$CT_TEST_WORK/`expected'"') local ++found
        file read `f' line
    }
    file close `f'
    assert `found' == 1
end

ct_fixture
