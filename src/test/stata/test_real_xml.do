version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_real_xml" "real"
    exit
}
do "$CTOSIM_TESTDIR/helpers.do"
copy "$CTOSIM_TESTDIR/fixtures/config.json" "input files/config.json", replace
ctosim using "input files/survey.xml", config("input files/config.json") saving("configured") clear
assert r(N) == 7
assert r(seed) == 1234
assert r(warnings) == 0
assert "`r(form_id)'" == "stata_fixture"
assert _N == 7
assert quantity == 23
assert answer == "café, " + char(34) + "hello" + char(34) + char(10) + "second line"
assert strpos(photo, "https://example.surveycto.com/") == 1
local wide `"`r(wide)'"'
save "loaded.dta", replace
import delimited using `"`wide'"', clear varnames(1) encoding("utf-8") bindquotes(strict)
cf _all using "loaded.dta", all

// n and seed override config values; other config values still apply.
ctosim using "input files/survey.xml", config("input files/config.json") ///
    n(1) seed(-9) server(my-test-server) saving("overrides") clear
assert r(N) == 1
assert r(seed) == -9
assert _N == 1
assert quantity == 23
assert strpos(photo, "https://my-test-server.surveycto.com/") == 1

// No configuration: defaults come from spec 04.
ctosim using "input files/survey.xml", saving("defaults") clear
assert r(N) == 100
assert r(seed) == 1
assert _N == 100
