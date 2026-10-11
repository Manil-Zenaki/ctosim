version 16.0
args child
if "`child'" != "child" {
    local failed 0
    foreach profile in override_jar override_zip override_other override_relative {
        capture noisily do "$CTOSIM_TESTDIR/converter-name-launch.do" "test_converter_name_override" "`profile'"
        if _rc local failed 1
    }
    assert `failed' == 0
    exit
}
do "$CTOSIM_TESTDIR/converter-name-helpers.do"
// Both fallback names exist, but the override must be imported regardless of suffix.
cn_conversion
