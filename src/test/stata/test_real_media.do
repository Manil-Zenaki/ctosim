version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_real_media" "real"
    exit
}
do "$CTOSIM_TESTDIR/helpers.do"
copy "$CTOSIM_TESTDIR/fixtures/media.xml" "input files/media.xml", replace
copy "$CTOSIM_TESTDIR/fixtures/media.fields.json" "input files/media.fields.json", replace
copy "$CTOSIM_TESTDIR/fixtures/people.csv" "input files/people.csv", replace
capture mkdir "other media"
copy "$CTOSIM_TESTDIR/fixtures/alternate_people.csv" "other media/people.csv", replace
ctosim using "input files/media.xml", n(1) saving("default media") clear
assert r(warnings) == 0
assert lookup == "Default person"
ctosim using "input files/media.xml", n(1) media("other media") saving("explicit media") clear
assert r(warnings) == 0
assert lookup == "Alternate person"

// A missing attachment is a warning and still permits a successful import.
capture mkdir "empty media"
quietly log using "missing_attachment.log", text replace name(capture11)
ctosim using "input files/media.xml", n(1) media("empty media") saving("warning media") clear
assert r(warnings) == 1
quietly log close capture11
assert _N == 1
javacall StataTestSupport warningsInLog, jars(stata-test-support.jar) ///
    args("$CT_TEST_WORK/missing_attachment.log" "$CT_TEST_WORK/warning media/run-report.txt")
