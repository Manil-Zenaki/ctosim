version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_id_real_config" "real"
    exit
}
do "$CTOSIM_TESTDIR/id-helpers.do"
a7_plan_fixture
ctosim using "input files/plan.xml", config("input files/plan.json") saving("config id") clear
assert r(N) == 40
assert r(seed) == 27017
assert r(warnings) == 0
a7_unique_id config_id 40
assert missing(survey_id)

// The command's id replaces the configured question; it does not allocate both.
ctosim using "input files/plan.xml", config("input files/plan.json") id(survey_id) saving("option id") clear
assert r(N) == 40
assert r(warnings) == 0
a7_unique_id survey_id 40
assert missing(config_id)

// A later omitted option recovers the original config, even after n/seed copies.
ctosim using "input files/plan.xml", config("input files/plan.json") n(1) seed(-71) saving("single") clear
assert r(N) == 1
assert r(seed) == -71
a7_unique_id config_id 1
assert missing(survey_id)

// The option also works without any config (default integer range 0..100).
ctosim using "input files/survey.xml", id(quantity) n(50) seed(28411) saving("no config") clear
assert _N == 50
assert !missing(quantity)
assert inrange(quantity, 0, 100)
isid quantity
