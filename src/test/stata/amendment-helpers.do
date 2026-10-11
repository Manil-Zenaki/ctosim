version 16.0
do "$CTOSIM_TESTDIR/helpers.do"

program define ct_amendment_version, rclass
    javacall AmendmentTestSupport ownVersion, jars(amendment-test-support.jar) ///
        args("$CT_TEST_WORK/own-version.txt")
    tempname f
    file open `f' using "$CT_TEST_WORK/own-version.txt", read text
    file read `f' major
    file close `f'
    return scalar major = real("`major'")
end

program define ct_amendment_selected
    args expected
    foreach slot in override home path {
        capture confirm file "$CT_TEST_WORK/launched-`slot'.txt"
        local rc = _rc
        if "`slot'" == "`expected'" {
            assert `rc' == 0
            // A probe is allowed; a second simulator invocation is not.
            tempname f
            file open `f' using "$CT_TEST_WORK/launched-`slot'.txt", read text
            file read `f' line
            assert "`line'" == "invoked"
            file read `f' line
            assert r(eof) == 1
            file close `f'
        }
        else assert `rc' == 601
    }
    if "`expected'" == "own" {
        tempname f
        file open `f' using "$CT_TEST_WORK/runtime.txt", read text
        file read `f' home
        file close `f'
        javacall StataTestSupport samePaths, jars(stata-test-support.jar) ///
            args(`"`home'"' `"`c(java_home)'"')
    }
end

program define ct_amendment_selection
    ct_amendment_version
    local own = r(major)
    local expected "$CT_AMENDMENT_EXPECT"
    if "`expected'" == "own_or_home" {
        if `own' >= 17 local expected "own"
        else local expected "home"
    }
    if "`expected'" == "own_or_path" {
        if `own' >= 17 local expected "own"
        else local expected "path"
    }
    ct_scenario 0
    ctosim using "input files/survey.xml", saving("selection output") clear
    ct_report_results 3 -713 0 "selection output"
    ct_invoked
    assert _N == 3
    ct_amendment_selected "`expected'"
end
