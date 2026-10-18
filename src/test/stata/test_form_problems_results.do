version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_form_problems_results" "double"
    exit
}
do "$CTOSIM_TESTDIR/form-problems-helpers.do"
// Distinct field counts, not occurrences, observations, warnings or option values.
foreach errors in 1 3 12 {
    a6_case `errors' 2 31 1
    ctosim using "input files/survey.xml", n(1) seed(8) ///
        saving("results `errors'") fakepreload clear
    a6_results `errors' 2 31 "results `errors'" "people.csv villages.csv"
    assert _N == 25
    assert id == _n
}

