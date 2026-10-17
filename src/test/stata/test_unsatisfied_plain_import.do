version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_unsatisfied_plain_import" "double"
    exit
}
do "$CTOSIM_TESTDIR/unsatisfied-helpers.do"
a4_case 3
ct_memory
local original `"`c(pwd)'"'
ctosim using "input files/survey.xml", saving("plain output") clear
assert `"`c(pwd)'"' == `"`original'"'
assert _N == 25
assert id == _n
assert missing(answer)
assert note == `"café 東京, quoted "text""'
capture confirm variable keeper
assert _rc != 0
// Compare with the exact import semantics required by spec 11.
unab actual : _all
local types ""
foreach v of local actual {
    local t : type `v'
    local types "`types' `t'"
}
save "actual.dta", replace
import delimited using "plain output/Constraint fixture_WIDE.csv", clear ///
    varnames(1) encoding("utf-8") bindquotes(strict)
unab expected : _all
assert "`actual'" == "`expected'"
local expectedtypes ""
foreach v of local expected {
    local t : type `v'
    local expectedtypes "`expectedtypes' `t'"
}
assert "`types'" == "`expectedtypes'"
cf _all using "actual.dta"
