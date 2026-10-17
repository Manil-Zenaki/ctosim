version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_fakepreload_forwarding" "double"
    exit
}
do "$CTOSIM_TESTDIR/fakepreload-helpers.do"
a5_case one
// The bare option also works with the ordinary default output-directory rule.
ctosim using "input files/survey.xml", fakepreload clear
a5_results "people.csv" 0 0 "ctosim-out/survey"
a5_flag 1
copy "$CTOSIM_TESTDIR/fixtures/config.json" "input files/settings.json", replace
capture mkdir "attached files"
// The flag is valueless and composes with every simulator-valued option, for both input kinds.
foreach extension in xml xlsx {
    a5_case one
    ctosim using "input files/survey.`extension'", fakepreload n(7) seed(-91) ///
        config("input files/settings.json") media("attached files") ///
        server("preload-server") saving("option output `extension'") clear
    a5_results "people.csv" 0 0 "option output `extension'"
    a5_flag 1
    ct_form_argument "input files/survey.`extension'"
    ct_argument "-n" "7"
    ct_argument "-s" "-91"
    ct_argument "-c" "input files/settings.json"
    ct_argument "-m" "attached files"
    ct_argument "-o" "option output `extension'"
    ct_argument "--server" "preload-server"
}
