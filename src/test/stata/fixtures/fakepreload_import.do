version 16.0
confirm file "run-report.txt"
confirm file "attachments/people.csv"
display "A5_TEMPLATE_OUTPUT_MUST_BE_SUPPRESSED"
import delimited using "Preload fixture_WIDE.csv", clear ///
    varnames(1) encoding("utf-8") bindquotes(strict)
keep in 1/2
generate byte imported_by_a5_template = 1
quietly summarize id
cd ".."
