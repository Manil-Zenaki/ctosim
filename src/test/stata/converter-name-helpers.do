version 16.0
do "$CTOSIM_TESTDIR/helpers.do"

program define cn_conversion
    // Use the real simulator and the documented -m ctosim_xlsform boundary.
    // A tagged archive makes a wrong precedence decision observable in Python.
    ctosim using "input files/survey.xlsx", ///
        config("$CTOSIM_TESTDIR/fixtures/config.json") n(2) seed(87) ///
        saving("converted output") clear
    assert r(N) == 2
    assert r(seed) == 87
    assert "`r(form_id)'" == "stata_fixture"
    assert _N == 2
    assert quantity == 23
    confirm file "converted output/form.xml"
    confirm file "converted output/form.fields.json"
    confirm file "converted output/run-report.txt"
    if "$CT_CONVERTER_EXPECT" != "real" {
        tempname marker
        file open `marker' using "converter-selected.txt", read text
        file read `marker' selected
        assert "`selected'" == "$CT_CONVERTER_EXPECT"
        file read `marker' extra
        assert r(eof) == 1
        file close `marker'
    }
end
