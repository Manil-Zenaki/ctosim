version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_overwrite" "double"
    exit
}
do "$CTOSIM_TESTDIR/helpers.do"
ct_scenario 0
capture mkdir "existing"
tempname f
file open `f' using "existing/run-report.txt", write text replace
file write `f' "preexisting run report sentinel" _n
file close `f'
file open `f' using "existing/unrelated.txt", write text replace
file write `f' "unrelated sentinel" _n
file close `f'
ct_memory
capture noisily ctosim using "input files/survey.xml", saving("existing") clear
local rc = _rc
assert `rc' == 602
ct_not_invoked
ct_memory_unchanged
ct_contains "existing/run-report.txt" "preexisting run report sentinel"

// replace permits disk replacement; clear is independently required for memory.
capture noisily ctosim using "input files/survey.xml", saving("existing") replace
local rc = _rc
assert `rc' == 4
ct_not_invoked
ct_memory_unchanged
ctosim using "input files/survey.xml", saving("existing") replace clear
ct_report_results 3 -713 0 "existing"
ct_absent "existing/run-report.txt" "preexisting run report sentinel"
ct_contains "existing/unrelated.txt" "unrelated sentinel"

// An existing directory without a report is allowed, even if it has other files.
capture mkdir "no report"
copy "existing/unrelated.txt" "no report/unrelated.txt", replace
ctosim using "input files/survey.xml", saving("no report") clear
assert _N == 3
ct_contains "no report/unrelated.txt" "unrelated sentinel"

// The default directory has the same overwrite protection.
ctosim using "input files/survey.xml", clear
ct_scenario 0
capture noisily ctosim using "input files/survey.xml", clear
local rc = _rc
assert `rc' == 602
ct_not_invoked
ctosim using "input files/survey.xml", replace clear
assert _N == 3
