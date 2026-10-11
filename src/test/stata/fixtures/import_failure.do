version 16.0
confirm file "run-report.txt"
display "FAILING_IMPORT_OUTPUT_MUST_BE_SUPPRESSED_11"
clear
set obs 1
generate long template_partial = 73
label data "Partial import must survive"
cd ".."
error 459
