version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_fakepreload_plain_import" "double"
    exit
}
do "$CTOSIM_TESTDIR/fakepreload-helpers.do"
a5_case many 2
ct_memory
local original `"`c(pwd)'"'
ctosim using "input files/survey.xml", saving("plain output") fakepreload clear
a5_results "Zebra.csv alpha.csv ménages.csv" 2 0 "plain output"
assert `"`c(pwd)'"' == `"`original'"'
assert _N == 3
assert id == _n
capture confirm variable keeper
assert _rc != 0
unab actual : _all
local types ""
foreach v of local actual {
    local t : type `v'
    local types "`types' `t'"
}
save "actual.dta", replace
import delimited using "plain output/Preload fixture_WIDE.csv", clear ///
    varnames(1) encoding("utf-8") bindquotes(strict)
unab expected : _all
local expectedtypes ""
foreach v of local expected {
    local t : type `v'
    local expectedtypes "`expectedtypes' `t'"
}
assert "`actual'" == "`expected'"
assert "`types'" == "`expectedtypes'"
cf _all using "actual.dta"
