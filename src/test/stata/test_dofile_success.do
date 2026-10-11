version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_dofile_success" "double"
    exit
}
do "$CTOSIM_TESTDIR/helpers.do"
ct_scenario 0 2
ct_memory
copy "$CTOSIM_TESTDIR/fixtures/import_success.do" "input files/import é.do", replace
local original `"`c(pwd)'"'
quietly log using "import.log", text replace name(capture11)
ctosim using "input files/survey.xml", saving("import output") ///
    dofile("input files/import é.do") clear
ct_report_results 3 -713 2 "import output"
quietly log close capture11
assert `"`c(pwd)'"' == `"`original'"'
assert _N == 2
assert imported_by_template == 1
local label : data label
assert "`label'" == "Data left by SurveyCTO template"
ct_absent "import.log" "IMPORT_OUTPUT_MUST_BE_SUPPRESSED_11"
ct_contains "import.log" "SIMULATOR_STDOUT_11"
javacall StataTestSupport warningsInLog, jars(stata-test-support.jar) ///
    args("$CT_TEST_WORK/import.log" "$CT_TEST_WORK/import output/run-report.txt")
