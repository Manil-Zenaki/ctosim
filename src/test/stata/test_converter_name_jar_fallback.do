version 16.0
args child
if "`child'" != "child" {
    local failed 0
    foreach profile in jar_unset jar_empty real_jar {
        capture noisily do "$CTOSIM_TESTDIR/converter-name-launch.do" "test_converter_name_jar_fallback" "`profile'"
        if _rc local failed 1
    }
    assert `failed' == 0
    exit
}
do "$CTOSIM_TESTDIR/converter-name-helpers.do"
// No zip is discoverable. Both absent/empty overrides permit the installed jar.
// real_jar uses the unmodified built converter archive under its installed name.
cn_conversion
