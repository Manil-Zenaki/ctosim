version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_injected_problems_forwarding" "double"
    exit
}
do "$CTOSIM_TESTDIR/injected-problems-helpers.do"
copy "$CTOSIM_TESTDIR/fixtures/injected_short.json" "input files/config with spaces.json", replace
capture mkdir "attached files"
foreach ext in xml xlsx {
    a8_case 2 2
    ctosim using "input files/survey.`ext'", problems id(survey_id) fakepreload ///
        n(7) seed(-91) config("input files/config with spaces.json") ///
        media("attached files") server("test-server") saving("options `ext'") clear
    a8_results 2 2 "options `ext'"
    a8_flag "--problems" 1
    a8_flag "--fake-attachments" 1
    ct_form_argument "input files/survey.`ext'"
    ct_argument "--id" "survey_id"
    ct_argument "-n" "7"
    ct_argument "-s" "-91"
    ct_argument "-c" "input files/config with spaces.json"
    ct_argument "-m" "attached files"
    ct_argument "-o" "options `ext'"
    ct_argument "--server" "test-server"
}
