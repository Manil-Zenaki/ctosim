version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_injected_problems_config" "double"
    exit
}
do "$CTOSIM_TESTDIR/injected-problems-helpers.do"
copy "$CTOSIM_TESTDIR/fixtures/injected_short.json" "input files/config.json", replace
a8_case 2 1
quietly log using "config.log", text replace name(a8)
ctosim using "input files/survey.xml", config("input files/config.json") saving("config only") clear
local count = r(problems)
local key `"`r(answerkey)'"'
quietly log close a8
// log close may change r(), so results were captured immediately.
assert `count' == 2
assert `"`key'"' != ""
a8_flag "--problems" 0
ct_argument "-c" "input files/config.json"
a8_log "config.log" 2 1
