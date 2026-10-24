version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_injected_problems_real_zero" "real"
    exit
}
do "$CTOSIM_TESTDIR/helpers.do"
// An enabled all-zero object still produces a header-only key and r(problems)=0.
foreach flag in "" "problems" {
    local suffix = cond("`flag'" == "", "config", "flag")
    ctosim using "input files/survey.xml", `flag' config("$CTOSIM_TESTDIR/fixtures/injected_zero.json") ///
        saving("zero `suffix'") clear
    local count = r(problems)
    local key `"`r(answerkey)'"'
    assert `count' == 0
    assert _N == 20
    confirm file `"`key'"'
    import delimited using `"`key'"', clear varnames(1) case(preserve) encoding("utf-8")
    assert _N == 0
    unab columns : _all
    assert "`columns'" == "KEY enumerator family problem check field value clean_value related_KEY kind"
}
