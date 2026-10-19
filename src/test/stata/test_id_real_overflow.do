version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_id_real_overflow" "real"
    exit
}
do "$CTOSIM_TESTDIR/id-helpers.do"
a7_plan_fixture
tempname f
file open `f' using "input files/overflow.json", write text replace
file write `f' `"{"seed":27017,"submissions":120,"enumerators":2,"fieldworkDays":1,"missingRate":0,"integerRange":{"min":1,"max":200}}"' _n
file close `f'
// 60 visits per enumerator cannot fit in nine hours with >=10 minutes travel.
// This forces overflow even without relying on a particular daily quota.
quietly log using "overflow.log", text replace name(idoverflow)
ctosim using "input files/plan.xml", config("input files/overflow.json") id(survey_id) saving("overflow") clear
local report `"`r(report)'"'
assert r(N) == 120
assert r(warnings) == 1
quietly log close idoverflow
assert _N == 120
isid survey_id
a7_plan_times
quietly summarize a7_day, meanonly
assert r(max) > td(05jan2026)
a7_warning_display `"`report'"' "overflow.log"
