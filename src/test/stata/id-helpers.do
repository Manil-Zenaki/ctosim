version 16.0
do "$CTOSIM_TESTDIR/helpers.do"

program define a7_no_id_argument
    // Omission must not become --id "", a default question, or a stale earlier id.
    ct_absent "$CT_TEST_WORK/arguments.txt" "--id"
end

program define a7_id_argument
    // Unlike path-valued options, an id must never acquire a cwd prefix.
    args expected
    tempname f
    file open `f' using "$CT_TEST_WORK/arguments.txt", read text
    local found 0
    file read `f' line
    while r(eof) == 0 {
        if `"`macval(line)'"' == "--id" {
            file read `f' value
            assert `"`macval(value)'"' == `"`macval(expected)'"'
            local ++found
        }
        file read `f' line
    }
    file close `f'
    assert `found' == 1
end

program define a7_reject_id
    args option
    ct_scenario 0
    ct_memory
    local original `"`c(pwd)'"'
    capture noisily ctosim using "input files/survey.xml", `option' ///
        saving("invalid id output") clear
    local rc = _rc
    assert `rc' == 198
    ct_not_invoked
    ct_memory_unchanged
    assert `"`c(pwd)'"' == `"`original'"'
    capture confirm file "invalid id output/run-report.txt"
    assert _rc == 601
end

program define a7_plan_fixture
    foreach extension in xml fields.json {
        copy "$CTOSIM_TESTDIR/fixtures/id_plan.`extension'" "input files/plan.`extension'", replace
    }
    copy "$CTOSIM_TESTDIR/fixtures/id_plan.json" "input files/plan.json", replace
end

program define a7_unique_id
    args variable count
    assert _N == `count'
    confirm numeric variable `variable'
    assert !missing(`variable')
    assert inrange(`variable', 1001, 1100)
    assert `variable' == floor(`variable')
    isid `variable'
end

program define a7_plan_times
    // The fixture starts at 08:00 UTC; exported dates use spec 08's MDY format.
    generate double a7_start = clock(starttime, "MDYhms")
    generate double a7_end = clock(endtime, "MDYhms")
    assert !missing(a7_start, a7_end)
    assert a7_end >= a7_start
    generate long a7_day = dofc(a7_start)
    generate double a7_time = a7_start - cofd(a7_day)
    assert inrange(a7_time, 8*60*60*1000, 17*60*60*1000)
    assert a7_day >= td(05jan2026)
    assert inlist(username, "enumerator01", "enumerator02")
    // Includes boundaries between working days, not just interviews on the same day.
    sort username a7_start
    by username: assert a7_start >= a7_end[_n-1] + 10*60*1000 if _n > 1
    by username: assert _N > 1
end

program define a7_warning_display
    args report logfile
    tempname f
    file open `f' using "`report'", read text
    local inwarnings 0
    local count 0
    file read `f' line
    while r(eof) == 0 {
        if substr(`"`macval(line)'"', 1, 9) == "Warnings:" local inwarnings 1
        else if `inwarnings' & trim(`"`macval(line)'"') != "" {
            local needle = trim(`"`macval(line)'"')
            ct_contains "`logfile'" `"`macval(needle)'"'
            local ++count
        }
        file read `f' line
    }
    file close `f'
    assert `count' > 0
end
