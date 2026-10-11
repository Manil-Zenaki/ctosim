version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_xml_no_converter" "xml_without_python"
    exit
}
do "$CTOSIM_TESTDIR/helpers.do"
ct_scenario 0
// An XML form does not require the invalid converter/Python environment paths.
ctosim using "input files/survey.xml", saving("xml only") clear
ct_report_results 3 -713 0 "xml only"
assert _N == 3
