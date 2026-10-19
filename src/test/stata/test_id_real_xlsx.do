version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_id_real_xlsx" "real"
    exit
}
do "$CTOSIM_TESTDIR/id-helpers.do"
// Conversion must not discard the command-line id setting.
ctosim using "input files/survey.xlsx", id(quantity) n(50) seed(28411) saving("xlsx ids") clear
assert r(N) == 50
assert _N == 50
assert !missing(quantity)
assert inrange(quantity, 0, 100)
isid quantity
