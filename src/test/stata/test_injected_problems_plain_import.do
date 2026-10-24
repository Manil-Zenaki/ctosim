version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_injected_problems_plain_import" "double"
    exit
}
do "$CTOSIM_TESTDIR/injected-problems-helpers.do"
a8_case 2 1
ct_memory
local original `"`c(pwd)'"'
ctosim using "input files/survey.xml", problems saving("plain output") clear
a8_results 2 1 "plain output"
assert `"`c(pwd)'"' == `"`original'"'
assert _N == 3
capture confirm variable keeper
assert _rc != 0
unab actual : _all
local types ""
foreach v of local actual {
    local t : type `v'
    local types "`types' `t'"
}
save "actual.dta", replace
// Native import is the independent oracle for names, values, types and row order.
import delimited using "plain output/Survey échantillon_WIDE.csv", clear ///
    varnames(1) encoding("utf-8") bindquotes(strict)
unab expected : _all
local expectedtypes ""
foreach v of local expected {
    local t : type `v'
    local expectedtypes "`expectedtypes' `t'"
}
assert "`actual'" == "`expected'"
assert "`types'" == "`expectedtypes'"
cf _all using "actual.dta", all
