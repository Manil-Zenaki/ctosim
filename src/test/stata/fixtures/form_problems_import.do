version 16.0
confirm file "run-report.txt"
confirm file "Problems_WIDE.csv"
display "A6_TEMPLATE_OUTPUT_MUST_BE_SUPPRESSED"
import delimited using "Problems_WIDE.csv", clear varnames(1) encoding("utf-8") bindquotes(strict)
keep in 1/2
generate byte imported_by_a6_template = 1
label data "Amendment 6 template completed"
// Replace r() to catch a summary/count taken from the importer's results.
quietly summarize id
cd ".."
// The tail log records only output after the template's work has finished.
quietly log on a6tail
