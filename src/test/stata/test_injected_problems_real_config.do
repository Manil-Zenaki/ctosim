version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_injected_problems_real_config" "real"
    exit
}
do "$CTOSIM_TESTDIR/helpers.do"
foreach ext in xml fields.json {
    copy "$CTOSIM_TESTDIR/fixtures/id_plan.`ext'" "input files/plan.`ext'", replace
}
// Configuration alone enables injection; the flag preserves configured rates.
foreach flag in "" "problems" {
    local suffix = cond("`flag'" == "", "config", "flag")
    ctosim using "input files/plan.xml", `flag' config("$CTOSIM_TESTDIR/fixtures/injected_short.json") ///
        saving("real `suffix'") clear
    local count = r(problems)
    local key `"`r(answerkey)'"'
    local wide `"`r(wide)'"'
    assert `count' == 5
    assert _N == 20
    capture confirm variable problem
    assert _rc != 0
    save "data `suffix'.dta", replace
    import delimited using `"`key'"', clear varnames(1) case(preserve) encoding("utf-8") bindquotes(strict)
    assert _N == 5
    isid KEY
    assert problem == "short"
    assert family == "duration"
    assert kind == "statistical"
    // Key rows join to actual submissions through KEY, never row position.
    rename KEY key
    merge 1:1 key using "data `suffix'.dta"
    assert inlist(_merge, 2, 3)
    count if _merge == 3
    assert r(N) == 5
}
javacall StataTestSupport equalDirectories, jars(stata-test-support.jar) ///
    args("$CT_TEST_WORK/real config" "$CT_TEST_WORK/real flag")
