version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_real_xlsx" "real_configured_python"
    exit
}
do "$CTOSIM_TESTDIR/helpers.do"
copy "$CTOSIM_TESTDIR/fixtures/form.xlsx" "input files/form.XLSX", replace
ctosim using "input files/form.XLSX", config("$CTOSIM_TESTDIR/fixtures/config.json") ///
    n(2) seed(87) saving("converted") clear
assert r(N) == 2
assert r(seed) == 87
assert "`r(form_id)'" == "stata_fixture"
assert _N == 2
assert quantity == 23
assert answer == "café, " + char(34) + "hello" + char(34) + char(10) + "second line"
confirm file "converted/form.xml"
confirm file "converted/form.fields.json"
confirm file "converted/run-report.txt"
// Conversion must not modify its source.
javacall StataTestSupport equalFiles, jars(stata-test-support.jar) ///
    args("$CT_TEST_WORK/input files/form.XLSX" "$CTOSIM_TESTDIR/fixtures/form.xlsx")
