version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_fakepreload_real_media" "real"
    exit
}
do "$CTOSIM_TESTDIR/helpers.do"
copy "$CTOSIM_TESTDIR/fixtures/media.xml" "input files/media.xml", replace
copy "$CTOSIM_TESTDIR/fixtures/media.fields.json" "input files/media.fields.json", replace
capture mkdir "empty media"
ctosim using "input files/media.xml", n(3) seed(481) media("empty media") ///
    saving("invented media") fakepreload clear
local fake `"`r(fakepreload)'"'
local warnings = r(warnings)
assert "`fake'" == "people.csv"
assert `warnings' == 0
assert _N == 3
confirm file "invented media/attachments/people.csv"
ct_absent "invented media/run-report.txt" "attached file people.csv not found"
save "first-run.dta", replace
ctosim using "input files/media.xml", n(3) seed(481) media("empty media") ///
    saving("same seed media") fakepreload clear
assert `"`r(fakepreload)'"' == "people.csv"
cf _all using "first-run.dta"
javacall StataTestSupport equalDirectories, jars(stata-test-support.jar) ///
    args("$CT_TEST_WORK/invented media" "$CT_TEST_WORK/same seed media")

// Generated files are usable media, and user edits to them are honored.
copy "$CTOSIM_TESTDIR/fixtures/alternate_people.csv" ///
    "invented media/attachments/people.csv", replace
ctosim using "input files/media.xml", n(3) seed(481) ///
    media("invented media/attachments") saving("reused media") fakepreload clear
local fake `"`r(fakepreload)'"'
local warnings = r(warnings)
assert "`fake'" == ""
assert `warnings' == 0
assert lookup == "Alternate person"
ct_absent "reused media/run-report.txt" "Fictitious attachments:"
// Without the option, the same reusable files still work with ordinary media().
ctosim using "input files/media.xml", n(3) seed(481) ///
    media("invented media/attachments") saving("ordinary reuse") clear
assert lookup == "Alternate person"
