version 16.0
args child
if "`child'" != "child" {
    do "$CTOSIM_TESTDIR/launch.do" "test_form_problems_display" "double"
    exit
}
do "$CTOSIM_TESTDIR/form-problems-helpers.do"
// Check every line, optional row numbers, repeated submission lines, Unicode,
// quotes and literal XPath; adjacent optional sections must not be swallowed.
foreach errors in 1 3 12 {
    a6_case `errors' 2 3 1
    quietly log using "sections-`errors'.smcl", smcl replace name(a6)
    ctosim using "input files/survey.xml", saving("sections `errors'") fakepreload clear
    quietly log close a6
    a6_log "sections-`errors'.smcl" "sections `errors'"
}

