version 16.0
args child
if "`child'" != "child" {
    local failed 0
    foreach profile in missing_unset missing_empty missing_override {
        capture noisily do "$CTOSIM_TESTDIR/converter-name-launch.do" "test_converter_name_missing" "`profile'"
        if _rc local failed 1
    }
    assert `failed' == 0
    exit
}
do "$CTOSIM_TESTDIR/converter-name-helpers.do"
ct_scenario 0
ct_memory
local original_pwd `"`c(pwd)'"'
quietly log using "missing.log", text replace name(converter_name)
capture noisily ctosim using "input files/survey.xlsx", saving("missing output") clear
local rc = _rc
quietly log close converter_name
assert `rc' == 601
ct_not_invoked
ct_memory_unchanged
assert `"`c(pwd)'"' == `"`original_pwd'"'
capture confirm file "converter-selected.txt"
assert _rc == 601
javacall StataTestSupport missingDiagnostic, jars(stata-test-support.jar) ///
    args("$CT_TEST_WORK/missing.log" "converter")
