version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_runtime_selection" "configured_python"
    exit
}
do "$CTOSIM_TESTDIR/helpers.do"
ct_scenario 0
ctosim using "input files/survey.xlsx", saving("configured python") clear
ct_report_results 3 -713 0 "configured python"
ct_argument "--python" "$CT_TEST_PYTHON"
ct_absent "$CT_TEST_WORK/arguments.txt" "must not use this python.exe"
// Actual child Java is the runtime used by Stata, not JAVA_HOME or PATH.
local home = subinstr(c(java_home), "\", "/", .)
tempname f
file open `f' using "$CT_TEST_WORK/runtime.txt", read text
file read `f' runtime
file close `f'
local runtime = subinstr(`"`runtime'"', "\", "/", .)
while substr("`home'", -1, 1) == "/" {
    local home = substr("`home'", 1, strlen("`home'") - 1)
}
assert lower("`runtime'") == lower("`home'")
