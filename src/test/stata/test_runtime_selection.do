version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_runtime_selection" "configured_python"
    exit
}
do "$CTOSIM_TESTDIR/helpers.do"
ct_scenario 0
ctosim using "input files/survey.xlsx", saving("configured python") clear
ct_report_results 3 -713 0 "configured python"
ct_argument "--python" "$CT_TEST_PYTHON"
ct_absent "$CT_TEST_WORK/arguments.txt" "must not use this python.exe"
// Amendment 1 Java precedence is covered by test_amendment_java_*.do.
