version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_injected_problems_results" "double"
    exit
}
do "$CTOSIM_TESTDIR/injected-problems-helpers.do"
// Nonzero, zero, and single-target results; count comes from the report.
foreach count in 2 0 1 {
    a8_case `count' 2
    ctosim using "input files/survey.xml", problems n(97) seed(11) ///
        saving("résultats `count'") clear
    a8_results `count' 2 "résultats `count'"
    // Two answer-key rows per target; neither its row count nor _N is r(problems).
    preserve
    import delimited using "résultats `count'/answer-key.csv", clear varnames(1) encoding("utf-8")
    assert _N == 2 * `count'
    restore
}
// Default output directory also yields an absolute answer-key path.
a8_case 1
ctosim using "input files/survey.xml", problems clear
a8_results 1 0 "ctosim-out/survey"
