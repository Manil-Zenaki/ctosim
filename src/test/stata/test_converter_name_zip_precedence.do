version 16.0
args child
if "`child'" != "child" {
    local failed 0
    foreach profile in zip_only zip_both zip_later {
        capture noisily do "$CTOSIM_TESTDIR/converter-name-launch.do" "test_converter_name_zip_precedence" "`profile'"
        if _rc local failed 1
    }
    assert `failed' == 0
    exit
}
do "$CTOSIM_TESTDIR/converter-name-helpers.do"
// Search the entire ado-path for zip before considering any jar, even an earlier jar.
cn_conversion
