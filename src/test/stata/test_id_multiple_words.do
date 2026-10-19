version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_id_multiple_words" "double"
    exit
}
do "$CTOSIM_TESTDIR/id-helpers.do"
a7_reject_id "id(household respondent)"
a7_reject_id `"id("household respondent")"'
a7_reject_id `"id("household   respondent extra")"'
local tab = char(9)
a7_reject_id `"id("household`tab'respondent")"'
local newline = char(10)
a7_reject_id `"id("household`newline'respondent")"'
