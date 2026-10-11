version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_ado_unset" "ado_unset"
    exit
}
do "$CTOSIM_TESTDIR/helpers.do"
ct_scenario 0
// Entirely absent overrides have the same fallback as empty overrides.
ctosim using "input files/survey.xlsx", saving("ado unset") clear
ct_report_results 3 -713 0 "ado unset"
ct_invoked
