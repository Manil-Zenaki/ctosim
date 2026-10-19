version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_id_omission" "double"
    exit
}
do "$CTOSIM_TESTDIR/id-helpers.do"
ct_scenario 0
ctosim using "input files/survey.xml", saving("initial no id") clear
a7_no_id_argument
ctosim using "input files/survey.xml", id(quantity) saving("with id") clear
a7_id_argument "quantity"
ctosim using "input files/survey.xml", saving("later no id") clear
a7_no_id_argument
copy "$CTOSIM_TESTDIR/fixtures/id_plan.json" "input files/settings.json", replace
ctosim using "input files/survey.xml", config("input files/settings.json") ///
    saving("configured id") clear
// The simulator reads the configuration's id; no overriding CLI id is invented.
a7_no_id_argument
ct_argument "-c" "input files/settings.json"
