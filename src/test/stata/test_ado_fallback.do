version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_ado_fallback" "ado_fallback"
    exit
}
do "$CTOSIM_TESTDIR/helpers.do"
ct_scenario 0
// Empty environment overrides resolve the jar and converter along the ado-path.
ctosim using "input files/survey.xlsx", saving("ado fallback") clear
ct_report_results 3 -713 0 "ado fallback"
ct_invoked
