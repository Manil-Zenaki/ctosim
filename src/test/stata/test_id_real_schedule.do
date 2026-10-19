version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_id_real_schedule" "real"
    exit
}
do "$CTOSIM_TESTDIR/id-helpers.do"
a7_plan_fixture
copy "$CTOSIM_TESTDIR/fixtures/id_plan_schedule.json" "input files/plan.json", replace
// The fieldwork plan applies even with no id option or configured id.
ctosim using "input files/plan.xml", config("input files/plan.json") saving("schedule") clear
assert _N == 40
assert r(warnings) == 0
a7_plan_times

// Observable village consequence: all pairs of GPS answers per enumerator/day
// are within 3 km + two 30 m answer offsets (spec 13), not just adjacent pairs.
confirm numeric variable gps_latitude gps_longitude
assert !missing(gps_latitude, gps_longitude)
sort username a7_day a7_start
by username a7_day: generate long a7_group_size = _N
quietly count if a7_group_size > 1
assert r(N) > 0
forvalues i = 1/`=_N' {
    forvalues j = 1/`=_N' {
        if username[`i'] == username[`j'] & a7_day[`i'] == a7_day[`j'] {
            local p = c(pi)/180
            local h = sin((gps_latitude[`i']-gps_latitude[`j'])*`p'/2)^2 + ///
                cos(gps_latitude[`i']*`p')*cos(gps_latitude[`j']*`p') * ///
                sin((gps_longitude[`i']-gps_longitude[`j'])*`p'/2)^2
            local distance = 2*6371000*asin(min(1,sqrt(`h')))
            assert `distance' <= 3060.1
        }
    }
}
