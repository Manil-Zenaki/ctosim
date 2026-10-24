version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_injected_problems_real_defaults_determinism" "real"
    exit
}
do "$CTOSIM_TESTDIR/helpers.do"
foreach ext in xml fields.json {
    copy "$CTOSIM_TESTDIR/fixtures/id_plan.`ext'" "input files/plan.`ext'", replace
}
foreach output in first second {
    ctosim using "input files/plan.xml", problems ///
        config("$CTOSIM_TESTDIR/fixtures/injected_defaults.json") saving("`output'") clear
    local count = r(problems)
    local key `"`r(answerkey)'"'
    assert `count' > 0 & `count' <= 20
    assert _N == 20
    if "`output'" == "first" save "first.dta", replace
    else cf _all using "first.dta", all
    preserve
    import delimited using `"`key'"', clear varnames(1) case(preserve) encoding("utf-8")
    egen byte target = tag(KEY)
    quietly count if target
    assert r(N) == `count'
    restore
}
javacall StataTestSupport equalDirectories, jars(stata-test-support.jar) ///
    args("$CT_TEST_WORK/first" "$CT_TEST_WORK/second")
ctosim using "input files/plan.xml", problems ///
    config("$CTOSIM_TESTDIR/fixtures/injected_defaults.json") saving("second") replace clear
cf _all using "first.dta", all
javacall StataTestSupport equalDirectories, jars(stata-test-support.jar) ///
    args("$CT_TEST_WORK/first" "$CT_TEST_WORK/second")
