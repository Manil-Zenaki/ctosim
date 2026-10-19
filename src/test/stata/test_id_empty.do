version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_id_empty" "double"
    exit
}
do "$CTOSIM_TESTDIR/id-helpers.do"
a7_reject_id "id()"
a7_reject_id `"id("")"'
a7_reject_id `"id("   ")"'
