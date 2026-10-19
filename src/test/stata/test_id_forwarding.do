version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_id_forwarding" "double"
    exit
}
do "$CTOSIM_TESTDIR/id-helpers.do"
// A question name is a literal, case-sensitive word, not a Stata varlist.
local index 0
foreach name in x Household_ID2 abcdefghijklmnopqrstuvwxyz123456 ménage_id {
    local ++index
    ct_scenario 0
    ctosim using "input files/survey.xml", id(`name') saving("literal `index'") clear
    ct_report_results 3 -713 0 "literal `index'"
    a7_id_argument "`name'"
}
// Quoting one word does not forward quote characters to the simulator.
ct_scenario 0
ctosim using "input files/survey.xml", id("Household_ID2") saving("quoted word") clear
a7_id_argument "Household_ID2"

copy "$CTOSIM_TESTDIR/fixtures/id_plan.json" "input files/settings.json", replace
capture mkdir "attached files"
foreach extension in xml xlsx {
    ct_scenario 0 2 3 -713
    ctosim using "input files/survey.`extension'", id(Household_ID2) n(7) seed(-91) ///
        config("input files/settings.json") media("attached files") ///
        server("id-server") saving("options `extension'") clear
    ct_report_results 3 -713 2 "options `extension'"
    a7_id_argument "Household_ID2"
    ct_form_argument "input files/survey.`extension'"
    ct_argument "-n" "7"
    ct_argument "-s" "-91"
    ct_argument "-c" "input files/settings.json"
    ct_argument "-m" "attached files"
    ct_argument "-o" "options `extension'"
    ct_argument "--server" "id-server"
}
