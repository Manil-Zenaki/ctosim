version 16.0
// This smoke check intentionally imposes no SMCL layout or prose wording.
findfile ctosim.sthlp
confirm file "`r(fn)'"
help ctosim
