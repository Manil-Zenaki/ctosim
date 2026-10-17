*! version 0.1.0  ctosim: simulate submissions of a SurveyCTO form. See specs/11-stata-command.md.
program define ctosim, rclass
    version 16
    * syntax treats n() and seed() like absent options; an empty value is an error here
    if ustrregexm(`"`0'"', "(^|[ ,])(n|seed)\(\s*\)") {
        display as error "n() and seed() need a value"
        exit 198
    }
    syntax using/ [, N(string) SEED(string) CONFIG(string) SAVING(string) MEDIA(string) ///
        SERVER(string) DOFILE(string) REPLACE CLEAR]

    local form `"`using'"'

    * ---- checks, before running anything ----
    if c(changed) & "`clear'" == "" {
        error 4
    }
    capture confirm file `"`form'"'
    if _rc {
        display as error `"file `form' not found"'
        exit 601
    }
    local lower = lower(`"`form'"')
    local xlsx = substr("`lower'", -5, .) == ".xlsx"
    if !`xlsx' & substr("`lower'", -4, .) != ".xml" {
        display as error `"`form' is neither a SurveyCTO XLSForm (.xlsx) nor an XForm (.xml)"'
        exit 198
    }
    foreach f in config dofile {
        if `"``f''"' != "" {
            capture confirm file `"``f''"'
            if _rc {
                display as error `"`f'(): file ``f'' not found"'
                exit 601
            }
        }
    }
    if "`n'" != "" {
        if !ustrregexm("`n'", "^[0-9]+$") {
            display as error "n() must be an integer from 1 to 1000000"
            exit 198
        }
        if real("`n'") < 1 | real("`n'") > 1000000 {
            display as error "n() must be an integer from 1 to 1000000"
            exit 198
        }
    }
    if "`seed'" != "" & !ustrregexm("`seed'", "^-?[0-9]+$") {
        display as error "seed() must be an integer"
        exit 198
    }

    * output directory, as an absolute path
    if `"`saving'"' == "" {
        mata: st_local("base", pathrmsuffix(pathbasename(st_local("form"))))
        local saving `"ctosim-out/`base'"'
    }
    _ctosim_abspath `"`saving'"'
    local outdir `"`r(path)'"'
    capture confirm file `"`outdir'/run-report.txt"'
    if !_rc & "`replace'" == "" {
        display as error `"`outdir' already holds a run; use option replace"'
        exit 602
    }

    * ---- programs ----
    local jar : env CTOSIM_JAR
    if `"`jar'"' == "" {
        capture findfile ctosim.jar
        if !_rc local jar `"`r(fn)'"'
    }
    if `"`jar'"' == "" {
        display as error "the simulator ctosim.jar was not found; reinstall the package with net install ctosim"
        exit 601
    }
    capture confirm file `"`jar'"'
    if _rc {
        display as error `"the simulator `jar' was not found (CTOSIM_JAR)"'
        exit 601
    }
    * the helper that runs Java programs for Stata (javacall), installed with the command
    capture findfile ctosim_bridge.jar
    if _rc {
        display as error "the helper ctosim_bridge.jar was not found; reinstall the package with net install ctosim"
        exit 601
    }
    local bridge `"`r(fn)'"'

    * a Java 17 or newer (spec 11, Amendment 1)
    tempfile javafound
    javacall ctosim.stata.StataBridge findJava, jars(ctosim_bridge.jar) args(`"`javafound'"')
    tempname fh
    file open `fh' using `"`javafound'"', read text
    file read `fh' java
    if `"`java'"' == "" {
        file read `fh' line
        while r(eof) == 0 {
            display as error `"`macval(line)'"'
            file read `fh' line
        }
        file close `fh'
        exit 601
    }
    file close `fh'
    local converter ""
    local python ""
    if `xlsx' {
        local converter : env CTOSIM_CONVERTER
        * net install copies the archive as ctosim_converter.jar (it skips .zip files)
        foreach name in ctosim_converter.zip ctosim_converter.jar {
            if `"`converter'"' == "" {
                capture findfile `name'
                if !_rc local converter `"`r(fn)'"'
            }
        }
        capture confirm file `"`converter'"'
        if `"`converter'"' == "" | _rc {
            display as error "the converter ctosim_converter.zip was not found; reinstall the package with net install ctosim"
            exit 601
        }
        capture quietly python query
        if !_rc local python `"`r(execpath)'"'
        if `"`python'"' != "" {
            capture confirm file `"`python'"'
            if _rc {
                display as error `"the Python set for Stata (`python') does not exist; converting an XLSForm needs one:"'
                display as error `"  run {cmd:python search} to find one, then {cmd:python set exec} {it:path}, permanently"'
                exit 601
            }
        }
        if `"`python'"' == "" local python : env CTOSIM_PYTHON
        if `"`python'"' == "" {
            display as error "no Python is set for Stata; converting an XLSForm needs one:"
            display as error `"  run {cmd:python search} to find one, then {cmd:python set exec} {it:path}, permanently"'
            exit 601
        }
    }

    * ---- run the simulator ----
    _ctosim_abspath `"`form'"'
    local args `""`r(path)'" -o "`outdir'""'
    if "`n'" != "" local args `"`args' -n `n'"'
    if "`seed'" != "" local args `"`args' -s `seed'"'
    if `"`config'"' != "" {
        _ctosim_abspath `"`config'"'
        local args `"`args' -c "`r(path)'""'
    }
    if `"`media'"' != "" {
        _ctosim_abspath `"`media'"'
        local args `"`args' -m "`r(path)'""'
    }
    if `"`server'"' != "" local args `"`args' --server "`server'""'
    if `"`python'"' != "" local args `"`args' --python "`python'""'

    tempfile out err status
    if `"`converter'"' == "" local converter "-"
    javacall ctosim.stata.StataBridge run, jars(ctosim_bridge.jar) ///
        args(`"`java'"' `"`jar'"' `"`converter'"' `"`out'"' `"`err'"' `"`status'"' `args')

    local code 1
    capture confirm file `"`status'"'
    if !_rc {
        file open `fh' using `"`status'"', read text
        file read `fh' line
        file close `fh'
        local code = real(strtrim(`"`macval(line)'"'))
    }
    _ctosim_show `"`out'"' text
    _ctosim_show `"`err'"' error
    if `code' == 2 exit 198
    if `code' != 0 exit 498

    * ---- the run report ----
    local report `"`outdir'/run-report.txt"'
    local warnings 0
    local wide ""
    file open `fh' using `"`report'"', read text
    file read `fh' line
    local section ""
    while r(eof) == 0 {
        if ustrregexm(`"`macval(line)'"', "^Form: .* \(([^,]+), version [^)]*\)$") local form_id = ustrregexs(1)
        else if ustrregexm(`"`macval(line)'"', "^Seed: (.+)$") local runseed = ustrregexs(1)
        else if ustrregexm(`"`macval(line)'"', "^Submissions: ([0-9]+)$") local runN = ustrregexs(1)
        else if `"`macval(line)'"' == "Files:" local section files
        else if ustrregexm(`"`macval(line)'"', "^Warnings: ([0-9]+)$") {
            local warnings = ustrregexs(1)
            local section warnings
            display as text "ctosim: " as result "`warnings'" as text " warnings"
        }
        else if substr(`"`macval(line)'"', 1, 2) == "  " {
            if "`section'" == "files" & `"`wide'"' == "" local wide = substr(`"`macval(line)'"', 3, .)
            if "`section'" == "warnings" mata: _ctosim_line("txt", st_local("line"))
        }
        file read `fh' line
    }
    file close `fh'

    * ---- load the data ----
    local widepath `"`outdir'/`wide'"'
    if `"`dofile'"' == "" {
        quietly import delimited using `"`widepath'"', varnames(1) encoding("utf-8") bindquotes(strict) clear
    }
    else {
        _ctosim_abspath `"`dofile'"'
        local dopath `"`r(path)'"'
        local here `"`c(pwd)'"'
        quietly cd `"`outdir'"'
        capture noisily run `"`dopath'"'
        local rc = _rc
        quietly cd `"`here'"'
        if `rc' exit `rc'
    }

    return scalar N = real("`runN'")
    return scalar warnings = real("`warnings'")
    return scalar seed = real("`runseed'")
    return local form_id `"`form_id'"'
    return local outdir `"`outdir'"'
    return local wide `"`widepath'"'
    return local report `"`report'"'
end

* An absolute path for a path relative to the current working directory.
program define _ctosim_abspath, rclass
    args path
    local p = subinstr(`"`path'"', "\", "/", .)
    if !(substr(`"`p'"', 1, 1) == "/" | ustrregexm(`"`p'"', "^[A-Za-z]:")) {
        local p `"`c(pwd)'/`p'"'
        local p = subinstr(`"`p'"', "\", "/", .)
    }
    return local path `"`p'"'
end

* Shows a text file line by line, as text or as an error message.
program define _ctosim_show
    args file style
    capture confirm file `"`file'"'
    if _rc exit
    tempname fh
    file open `fh' using `"`file'"', read text
    file read `fh' line
    while r(eof) == 0 {
        if "`style'" == "error" mata: _ctosim_line("err", st_local("line"))
        else mata: _ctosim_line("txt", st_local("line"))
        file read `fh' line
    }
    file close `fh'
end

* Shows one line exactly as written, in the given style (txt, err): display would expand the
* macro references in it, even through macval() inside a loop, and read its braces as SMCL.
version 16
mata:
void _ctosim_line(string scalar style, string scalar text)
{
    text = subinstr(text, "{", char(1))
    text = subinstr(text, "}", "{c )-}")
    text = subinstr(text, char(1), "{c -(}")
    printf("{" + style + "}%s\n", text)
}
end
