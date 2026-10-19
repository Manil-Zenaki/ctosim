version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_id_real_determinism" "real"
    exit
}
do "$CTOSIM_TESTDIR/id-helpers.do"
a7_plan_fixture
ctosim using "input files/plan.xml", config("input files/plan.json") id(survey_id) saving("first") clear
a7_unique_id survey_id 40
save "first.dta", replace
ctosim using "input files/plan.xml", config("input files/plan.json") id(survey_id) saving("second") clear
cf _all using "first.dta", all
javacall StataTestSupport equalDirectories, jars(stata-test-support.jar) args("$CT_TEST_WORK/first" "$CT_TEST_WORK/second")
ctosim using "input files/plan.xml", config("input files/plan.json") id(survey_id) saving("second") replace clear
cf _all using "first.dta", all
javacall StataTestSupport equalDirectories, jars(stata-test-support.jar) args("$CT_TEST_WORK/first" "$CT_TEST_WORK/second")
