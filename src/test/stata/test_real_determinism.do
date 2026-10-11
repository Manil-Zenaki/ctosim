version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_real_determinism" "real"
    exit
}
do "$CTOSIM_TESTDIR/helpers.do"
ctosim using "input files/survey.xml", n(25) seed(918) saving("first") clear
save "first.dta", replace
quietly datasignature
local signature "`r(datasignature)'"
ctosim using "input files/survey.xml", n(25) seed(918) saving("second") clear
cf _all using "first.dta", all
// Compare every file and the directory membership, not only the wide CSV.
javacall StataTestSupport equalDirectories, jars(stata-test-support.jar) args("$CT_TEST_WORK/first" "$CT_TEST_WORK/second")
ctosim using "input files/survey.xml", n(25) seed(918) saving("second") replace clear
cf _all using "first.dta", all
javacall StataTestSupport equalDirectories, jars(stata-test-support.jar) args("$CT_TEST_WORK/first" "$CT_TEST_WORK/second")

// Different seed has meaningful variation across 25 submissions.
ctosim using "input files/survey.xml", n(25) seed(919) saving("different") clear
quietly datasignature
assert "`r(datasignature)'" != "`signature'"

// Absolute FORM and different cwd still produce byte-identical runs (spec 10).
capture mkdir "other cwd"
cd "other cwd"
ctosim using "$CT_TEST_WORK/input files/survey.xml", n(25) seed(918) saving("third") clear
cf _all using "$CT_TEST_WORK/first.dta", all
javacall StataTestSupport equalDirectories, jars(stata-test-support.jar) ///
    args("$CT_TEST_WORK/first" "$CT_TEST_WORK/other cwd/third")
