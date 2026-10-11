version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_options_paths" "double"
    exit
}
do "$CTOSIM_TESTDIR/helpers.do"
ct_scenario 0 0 7 -99
copy "$CTOSIM_TESTDIR/fixtures/config.json" "input files/config é.json", replace
capture mkdir "media files"
local original `"`c(pwd)'"'
ctosim using "input files/survey.xml", n(7) seed(-99) ///
    config("input files/config é.json") saving("nested parent/output é") ///
    media("media files") server(test-server-11) clear
ct_report_results 7 -99 0 "nested parent/output é"
assert _N == 7
assert `"`c(pwd)'"' == `"`original'"'
ct_argument "-n" "7"
ct_argument "-s" "-99"
ct_argument "-c" "input files/config é.json"
ct_argument "-o" "nested parent/output é"
ct_argument "-m" "media files"
ct_argument "--server" "test-server-11"
ct_form_argument "input files/survey.xml"

// Absolute paths, uppercase/mixed case suffixes, and filename-based default output.
ct_scenario 0
local case = 0
foreach extension in XML xMl XLSX xLsX {
    local ++case
    if lower("`extension'") == "xml" local source "xml"
    else local source "xlsx"
    copy "input files/survey.`source'" "input files/café `extension' `case'.`extension'", replace
    copy "input files/survey.fields.json" "input files/café `extension' `case'.fields.json", replace
    ctosim using "$CT_TEST_WORK/input files/café `extension' `case'.`extension'", clear
    ct_report_results 3 -713 0 "ctosim-out/café `extension' `case'"
    assert _N == 3
}
