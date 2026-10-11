version 16.0
// Relative names deliberately require the output directory as cwd.
confirm file "run-report.txt"
confirm file "Survey échantillon_WIDE.csv"
display "IMPORT_OUTPUT_MUST_BE_SUPPRESSED_11"
import delimited using "Survey échantillon_WIDE.csv", clear varnames(1) encoding("utf-8") bindquotes(strict)
keep in 1/2
generate byte imported_by_template = 1
label variable imported_by_template "Template ran"
label data "Data left by SurveyCTO template"
// Deliberately clobber r(); ctosim must return the run report's values.
quietly summarize age
cd ".."
