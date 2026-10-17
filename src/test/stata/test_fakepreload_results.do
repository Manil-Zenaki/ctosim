version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_fakepreload_results" "double"
    exit
}
do "$CTOSIM_TESTDIR/fakepreload-helpers.do"
// Natural case-sensitive filename order, Unicode, CSV suffixes, and no path prefix.
// Reuse one directory: stale attachment files from the preceding run must not affect r().
foreach mode in one many none one {
    a5_case `mode' 2 1
    ctosim using "input files/survey.xml", saving("report results") ///
        n(7) seed(99) fakepreload clear replace
    local names ""
    if "`mode'" == "one" local names "people.csv"
    if "`mode'" == "many" local names "Zebra.csv alpha.csv ménages.csv"
    a5_results "`names'" 2 1 "report results"
}
