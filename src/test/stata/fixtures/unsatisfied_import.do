version 16.0
confirm file "run-report.txt"
confirm file "Constraint fixture_WIDE.csv"
display "A4_TEMPLATE_OUTPUT_MUST_BE_SUPPRESSED"
import delimited using "Constraint fixture_WIDE.csv", clear ///
    varnames(1) encoding("utf-8") bindquotes(strict)
keep in 1/2
generate byte imported_by_a4_template = 1
// Deliberately replace r(N), then change cwd: ctosim must restore both contracts.
quietly summarize id
cd ".."
