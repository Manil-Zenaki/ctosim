version 16.0
confirm file "run-report.txt"
confirm file "Problems_WIDE.csv"
display "A6_FAILING_TEMPLATE_OUTPUT_MUST_BE_SUPPRESSED"
clear
set obs 1
generate int template_partial = 73
label data "Amendment 6 partial import survives"
quietly summarize template_partial
cd ".."
quietly log on a6tail
error 459
