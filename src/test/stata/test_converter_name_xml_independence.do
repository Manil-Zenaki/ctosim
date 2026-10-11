version 16.0
args child
if "`child'" != "child" {
    local failed 0
    foreach profile in xml_missing xml_invalid_override {
        capture noisily do "$CTOSIM_TESTDIR/converter-name-launch.do" "test_converter_name_xml_independence" "`profile'"
        if _rc local failed 1
    }
    assert `failed' == 0
    exit
}
do "$CTOSIM_TESTDIR/converter-name-helpers.do"
// The amendment's converter discovery applies only to XLSForms.
ctosim using "input files/survey.xml", ///
    config("$CTOSIM_TESTDIR/fixtures/config.json") n(2) seed(87) ///
    saving("xml output") clear
assert r(N) == 2
assert r(seed) == 87
assert "`r(form_id)'" == "stata_fixture"
assert _N == 2
assert quantity == 23
confirm file "xml output/run-report.txt"
capture confirm file "converter-selected.txt"
assert _rc == 601
