version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_results_warnings" "double"
    exit
}
do "$CTOSIM_TESTDIR/helpers.do"
foreach count in 0 1 2 {
    ct_scenario 0 `count'
    quietly log using "warnings`count'.log", text replace name(capture11)
    ctosim using "input files/survey.xml", saving("warnings `count'") clear
    // These are report values, not the command's presumed n/seed defaults.
    ct_report_results 3 -713 `count' "warnings `count'"
    quietly log close capture11
    ct_contains "warnings`count'.log" "SIMULATOR_STDOUT_11"
    ct_contains "warnings`count'.log" "SIMULATOR_STDERR_11"
    javacall StataTestSupport warningsInLog, jars(stata-test-support.jar) ///
        args("$CT_TEST_WORK/warnings`count'.log" "$CT_TEST_WORK/warnings `count'/run-report.txt")
}
