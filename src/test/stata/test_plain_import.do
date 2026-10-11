version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_plain_import" "double"
    exit
}
do "$CTOSIM_TESTDIR/helpers.do"
ct_scenario 0
// Stata's own import is the oracle for naming, types, strict quotes and UTF-8.
import delimited using "$CTOSIM_TESTDIR/fixtures/wide.csv", clear ///
    varnames(1) encoding("utf-8") bindquotes(strict)
assert _N == 3
assert notes[1] == "café, 東京"
assert notes[2] == "said " + char(34) + "hello" + char(34)
assert strpos(notes[3], "first line") > 0
assert strpos(notes[3], "second line") > 0
unab expected_vars : _all
local types ""
foreach v of local expected_vars {
    local type : type `v'
    local types "`types' `type'"
}
save "expected_import.dta", replace
ct_memory
local original `"`c(pwd)'"'
ctosim using "input files/survey.xml", saving("plain import") clear
ct_report_results 3 -713 0 "plain import"
assert _N == 3
assert `"`c(pwd)'"' == `"`original'"'
cf _all using "expected_import.dta", all
unab actual_vars : _all
assert "`actual_vars'" == "`expected_vars'"
local actual_types ""
foreach v of local actual_vars {
    local type : type `v'
    local actual_types "`actual_types' `type'"
}
assert "`actual_types'" == "`types'"
capture confirm variable wrong_long_file
assert _rc != 0

// Single submission boundary and replacing empty memory.
clear
ct_scenario 0 0 1
ctosim using "input files/survey.xml", n(1) saving("single")
assert r(N) == 1
assert _N == 1
