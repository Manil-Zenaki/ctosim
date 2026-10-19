version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_id_fakepreload" "double"
    exit
}
do "$CTOSIM_TESTDIR/id-helpers.do"
local simulator : environment CTOSIM_JAR
copy "$CTOSIM_REPO/.codex-runs/stata-support/id-fixture.jar" "`simulator'", replace
ct_scenario 0
ctosim using "input files/survey.xml", id(quantity) fakepreload saving("id and preload") clear
assert `"`r(fakepreload)'"' == ""
ct_report_results 3 -713 0 "id and preload"
a7_id_argument "quantity"
tempname f
file open `f' using "$CT_TEST_WORK/arguments.txt", read text
local flags 0
file read `f' line
while r(eof) == 0 {
    if `"`macval(line)'"' == "--fake-attachments" local ++flags
    file read `f' line
}
file close `f'
assert `flags' == 1
