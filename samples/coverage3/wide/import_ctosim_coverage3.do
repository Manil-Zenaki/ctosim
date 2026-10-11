* import_ctosim_coverage3.do
*
* 	Imports and aggregates "ctosim coverage test 3" (ID: ctosim_coverage3) data.
*
*	Inputs:  "ctosim coverage test 3_WIDE.csv"
*	Outputs: "ctosim coverage test 3.dta"
*
*	Output by SurveyCTO October 11, 2026 7:51 PM.

* initialize Stata
clear all
set more off
set mem 100m

* initialize workflow-specific parameters
*	Set overwrite_old_data to 1 if you use the review and correction
*	workflow and allow un-approving of submissions. If you do this,
*	incoming data will overwrite old data, so you won't want to make
*	changes to data in your local .dta file (such changes can be
*	overwritten with each new import).
local overwrite_old_data 0

* initialize form-specific parameters
local csvfile "ctosim coverage test 3_WIDE.csv"
local dtafile "ctosim coverage test 3.dta"
local corrfile "ctosim coverage test 3_corrections.csv"
local note_fields1 ""
local text_fields1 "deviceid subscriberid simid devicephonenum username duration caseid text_audit audio_audit sv_list field_comments resp_code added_in_v2 sel_default never_shown f_div f_third f_product f_mod f_negative"
local text_fields2 "f_small_sum f_big f_div_zero f_not_a_number f_round f_int f_pow f_sqrt f_exp f_log10 f_pi f_abs f_min_max f_format_number f_string_of_whole f_true f_false f_if f_coalesce f_empty f_relevant_skipped"
local text_fields3 "f_concat f_substr f_string_length f_lower f_upper f_linebreak f_comma_quote f_regex f_hash f_today f_now f_format_date f_format_datetime f_date_minus_one f_date_from_number f_date_before_today"
local text_fields4 "f_decimal_date_time f_decimal_time f_version f_selected f_count_selected f_selected_at f_choice_label f_count_items f_item_at f_item_index f_item_present f_de_duplicate f_rank_value f_uuid f_random"
local text_fields5 "auto_count a_index_* a_value_* a_kind_* agg_count agg_sum agg_min agg_max agg_join agg_count_if agg_sum_if agg_join_if agg_min_if agg_max_if agg_indexed agg_rank agg_rank_if expr_rep_count e_index_*"
local text_fields6 "def_change t_comma_quote t_accents t_leading_zeros t_numbers t_numbers_decimal m_many m_blank s_other_specify districts_multi village village_label skipped_text deep_text person_count p_name_*"
local text_fields7 "p_school_* this_field_name_is_longer_than_t_* m_audio m_video m_file m_signature instanceid instancename"
local date_fields1 "dt_old dt_month_year dt_month dt_no_calendar visit_date_*"
local datetime_fields1 "submissiondate starttime endtime dtm_any"

disp
disp "Starting import of: `csvfile'"
disp

* import data from primary .csv file
insheet using "`csvfile'", names clear

* drop extra table-list columns
cap drop reserved_name_for_field_*
cap drop generated_table_list_lab*

* continue only if there's at least one row of data to import
if _N>0 {
	* drop note fields (since they don't contain any real data)
	forvalues i = 1/100 {
		if "`note_fields`i''" ~= "" {
			drop `note_fields`i''
		}
	}
	
	* format date and date/time fields
	forvalues i = 1/100 {
		if "`datetime_fields`i''" ~= "" {
			foreach dtvarlist in `datetime_fields`i'' {
				cap unab dtvarlist : `dtvarlist'
				if _rc==0 {
					foreach dtvar in `dtvarlist' {
						tempvar tempdtvar
						rename `dtvar' `tempdtvar'
						gen double `dtvar'=.
						cap replace `dtvar'=clock(`tempdtvar',"DMYhms",2025)
						* automatically try without seconds, just in case
						cap replace `dtvar'=clock(`tempdtvar',"DMYhm",2025) if `dtvar'==. & `tempdtvar'~=""
						format %tc `dtvar'
						drop `tempdtvar'
					}
				}
			}
		}
		if "`date_fields`i''" ~= "" {
			foreach dtvarlist in `date_fields`i'' {
				cap unab dtvarlist : `dtvarlist'
				if _rc==0 {
					foreach dtvar in `dtvarlist' {
						tempvar tempdtvar
						rename `dtvar' `tempdtvar'
						gen double `dtvar'=.
						cap replace `dtvar'=date(`tempdtvar',"DMY",2025)
						format %td `dtvar'
						drop `tempdtvar'
					}
				}
			}
		}
	}

	* ensure that text fields are always imported as strings (with "" for missing values)
	* (note that we treat "calculate" fields as text; you can destring later if you wish)
	tempvar ismissingvar
	quietly: gen `ismissingvar'=.
	forvalues i = 1/100 {
		if "`text_fields`i''" ~= "" {
			foreach svarlist in `text_fields`i'' {
				cap unab svarlist : `svarlist'
				if _rc==0 {
					foreach stringvar in `svarlist' {
						quietly: replace `ismissingvar'=.
						quietly: cap replace `ismissingvar'=1 if `stringvar'==.
						cap tostring `stringvar', format(%100.0g) replace
						cap replace `stringvar'="" if `ismissingvar'==1
					}
				}
			}
		}
	}
	quietly: drop `ismissingvar'


	* consolidate unique ID into "key" variable
	replace key=instanceid if key==""
	drop instanceid


	* label variables
	label variable key "Unique submission ID"
	cap label variable submissiondate "Date/time submitted"
	cap label variable formdef_version "Form version used on device"
	cap label variable review_status "Review status"
	cap label variable review_comments "Comments made during review"
	cap label variable review_corrections "Corrections made during review"


	label variable resp_code "Submission code: type D (version 2)"
	note resp_code: "Submission code: type D (version 2)"

	label variable added_in_v2 "New in version 2: type v2"
	note added_in_v2: "New in version 2: type v2"

	label variable sel_default "Pre-selected answer: leave Beta selected"
	note sel_default: "Pre-selected answer: leave Beta selected"

	label variable never_shown "Never shown"
	note never_shown: "Never shown"

	label variable n_auto "Number of computed rows (read only, 3)"
	note n_auto: "Number of computed rows (read only, 3)"

	label variable def_keep "Pre-filled 7: do NOT change it"
	note def_keep: "Pre-filled 7: do NOT change it"

	label variable def_change "Pre-filled 'abc': replace it with xyz"
	note def_change: "Pre-filled 'abc': replace it with xyz"

	label variable calc_default "Calculated (7 x 2): try to change it to 99"
	note calc_default: "Calculated (7 x 2): try to change it to 99"

	label variable t_comma_quote "Type exactly: Hello, 'world'"
	note t_comma_quote: "Type exactly: Hello, 'world'"

	label variable t_accents "Type exactly: Élodie à Ouagadougou, niño"
	note t_accents: "Type exactly: Élodie à Ouagadougou, niño"

	label variable t_leading_zeros "Type exactly: 007"
	note t_leading_zeros: "Type exactly: 007"

	label variable t_numbers "Type exactly: 012345678901"
	note t_numbers: "Type exactly: 012345678901"

	label variable t_numbers_decimal "Type exactly: 3.50"
	note t_numbers_decimal: "Type exactly: 3.50"

	label variable d_whole "Type exactly: 5"
	note d_whole: "Type exactly: 5"

	label variable d_trailing_zero "Type exactly: 0.10"
	note d_trailing_zero: "Type exactly: 0.10"

	label variable d_negative "Type exactly: -3.75"
	note d_negative: "Type exactly: -3.75"

	label variable i_negative "Type exactly: -12"
	note i_negative: "Type exactly: -12"

	label variable i_large "Type exactly: 123456789"
	note i_large: "Type exactly: 123456789"

	label variable i_minimum_time "Type 1 (this question has a minimum time)"
	note i_minimum_time: "Type 1 (this question has a minimum time)"

	label variable dt_old "Enter 15 June 1950"
	note dt_old: "Enter 15 June 1950"

	label variable dt_month_year "Enter March 2025"
	note dt_month_year: "Enter March 2025"

	label variable dt_month "Enter any month (note which)"
	note dt_month: "Enter any month (note which)"

	label variable dt_no_calendar "Enter 31 December 2025"
	note dt_no_calendar: "Enter 31 December 2025"

	label variable tm_after_midnight "Enter 00:05 (5 minutes past midnight)"
	note tm_after_midnight: "Enter 00:05 (5 minutes past midnight)"

	label variable tm_noon "Enter 12:30 (half past noon)"
	note tm_noon: "Enter 12:30 (half past noon)"

	label variable dtm_any "Enter 1 January 2026, 23:59"
	note dtm_any: "Enter 1 January 2026, 23:59"

	label variable s_minimal "Drop-down: choose Eleven"
	note s_minimal: "Drop-down: choose Eleven"
	label define s_minimal 1 "One" 2 "Two" 3 "Three" 4 "Four" 5 "Five" 6 "Six" 7 "Seven" 8 "Eight" 9 "Nine" 10 "Ten" 11 "Eleven" 12 "Twelve"
	label values s_minimal s_minimal

	label variable s_likert "Scale: choose Agree"
	note s_likert: "Scale: choose Agree"
	label define s_likert 1 "Strongly disagree" 2 "Disagree" 3 "Neutral" 4 "Agree" 5 "Strongly agree"
	label values s_likert s_likert

	label variable s_randomized "Shuffled list: choose Ten"
	note s_randomized: "Shuffled list: choose Ten"
	label define s_randomized 1 "One" 2 "Two" 3 "Three" 4 "Four" 5 "Five" 6 "Six" 7 "Seven" 8 "Eight" 9 "Nine" 10 "Ten" 11 "Eleven" 12 "Twelve"
	label values s_randomized s_randomized

	label variable m_many "Tick One, Ten and Twelve"
	note m_many: "Tick One, Ten and Twelve"

	label variable m_blank "Leave this question BLANK (tick nothing)"
	note m_blank: "Leave this question BLANK (tick nothing)"

	label variable s_comma_label "Choose 'Yes, sure'"
	note s_comma_label: "Choose 'Yes, sure'"
	label define s_comma_label 1 "Yes, sure" 0 "No, never"
	label values s_comma_label s_comma_label

	label variable s_other "Choose Other"
	note s_other: "Choose Other"
	label define s_other 1 "Maize" 2 "Teff" 96 "Other"
	label values s_other s_other

	label variable s_other_specify "Specify: type Bananas"
	note s_other_specify: "Specify: type Bananas"

	label variable region "Region: choose South"
	note region: "Region: choose South"
	label define region 1 "North" 2 "South"
	label values region region

	label variable district "District: choose South-B"
	note district: "District: choose South-B"
	label define district 11 "North-A" 12 "North-B" 21 "South-A" 22 "South-B"
	label values district district

	label variable districts_multi "Districts of the chosen region: tick both"
	note districts_multi: "Districts of the chosen region: tick both"

	label variable village "Village (loaded from villages.csv, South only): choose Hawassa"
	note village: "Village (loaded from villages.csv, South only): choose Hawassa"

	label variable fl_a "Type 1"
	note fl_a: "Type 1"

	label variable fl_b "Type 2"
	note fl_b: "Type 2"

	label variable tbl_header "How often?"
	note tbl_header: "How often?"
	label define tbl_header 1 "Never" 2 "Sometimes" 3 "Often"
	label values tbl_header tbl_header

	label variable tbl_water "Water: Often"
	note tbl_water: "Water: Often"
	label define tbl_water 1 "Never" 2 "Sometimes" 3 "Often"
	label values tbl_water tbl_water

	label variable tbl_power "Power: Never"
	note tbl_power: "Power: Never"
	label define tbl_power 1 "Never" 2 "Sometimes" 3 "Often"
	label values tbl_power tbl_power

	label variable skipped_text "Never shown"
	note skipped_text: "Never shown"

	label variable deep_text "Type: deep"
	note deep_text: "Type: deep"

	label variable n_people "Number of people: type 2 (C: type 0)"
	note n_people: "Number of people: type 2 (C: type 0)"

	label variable m_audio "Record 2 seconds of audio"
	note m_audio: "Record 2 seconds of audio"

	label variable m_video "Record 2 seconds of video (or skip on the web)"
	note m_video: "Record 2 seconds of video (or skip on the web)"

	label variable m_file "Attach any small file (or skip)"
	note m_file: "Attach any small file (or skip)"

	label variable m_signature "Sign here"
	note m_signature: "Sign here"

	label variable m_barcode "Scan any barcode (or skip on the web)"
	note m_barcode: "Scan any barcode (or skip on the web)"

	label variable g_trace "Record a short line of 2 to 3 points"
	note g_trace: "Record a short line of 2 to 3 points"

	label variable g_shape "Record a small shape of 3 to 4 points"
	note g_shape: "Record a small shape of 3 to 4 points"



	capture {
		foreach rgvar of varlist p_name_* {
			label variable `rgvar' "Name (any)"
			note `rgvar': "Name (any)"
		}
	}

	capture {
		foreach rgvar of varlist p_age_* {
			label variable `rgvar' "Age: person 1 is 30, person 2 is 5"
			note `rgvar': "Age: person 1 is 30, person 2 is 5"
		}
	}

	capture {
		foreach rgvar of varlist p_school_* {
			label variable `rgvar' "School name (any)"
			note `rgvar': "School name (any)"
		}
	}

	capture {
		foreach rgvar of varlist this_field_name_is_longer_than_t_* {
			label variable `rgvar' "Long field name: type x"
			note `rgvar': "Long field name: type x"
		}
	}

	capture {
		foreach rgvar of varlist visit_date_* {
			label variable `rgvar' "Visit date (any)"
			note `rgvar': "Visit date (any)"
		}
	}




	* append old, previously-imported data (if any)
	cap confirm file "`dtafile'"
	if _rc == 0 {
		* mark all new data before merging with old data
		gen new_data_row=1
		
		* pull in old data
		append using "`dtafile'"
		
		* drop duplicates in favor of old, previously-imported data if overwrite_old_data is 0
		* (alternatively drop in favor of new data if overwrite_old_data is 1)
		sort key
		by key: gen num_for_key = _N
		drop if num_for_key > 1 & ((`overwrite_old_data' == 0 & new_data_row == 1) | (`overwrite_old_data' == 1 & new_data_row ~= 1))
		drop num_for_key

		* drop new-data flag
		drop new_data_row
	}
	
	* save data to Stata format
	save "`dtafile'", replace

	* show codebook and notes
	codebook
	notes list
}

disp
disp "Finished import of: `csvfile'"
disp

* OPTIONAL: LOCALLY-APPLIED STATA CORRECTIONS
*
* Rather than using SurveyCTO's review and correction workflow, the code below can apply a list of corrections
* listed in a local .csv file. Feel free to use, ignore, or delete this code.
*
*   Corrections file path and filename:  ctosim coverage test 3_corrections.csv
*
*   Corrections file columns (in order): key, fieldname, value, notes

capture confirm file "`corrfile'"
if _rc==0 {
	disp
	disp "Starting application of corrections in: `corrfile'"
	disp

	* save primary data in memory
	preserve

	* load corrections
	insheet using "`corrfile'", names clear
	
	if _N>0 {
		* number all rows (with +1 offset so that it matches row numbers in Excel)
		gen rownum=_n+1
		
		* drop notes field (for information only)
		drop notes
		
		* make sure that all values are in string format to start
		gen origvalue=value
		tostring value, format(%100.0g) replace
		cap replace value="" if origvalue==.
		drop origvalue
		replace value=trim(value)
		
		* correct field names to match Stata field names (lowercase, drop -'s and .'s)
		replace fieldname=lower(subinstr(subinstr(fieldname,"-","",.),".","",.))
		
		* format date and date/time fields (taking account of possible wildcards for repeat groups)
		forvalues i = 1/100 {
			if "`datetime_fields`i''" ~= "" {
				foreach dtvar in `datetime_fields`i'' {
					* skip fields that aren't yet in the data
					cap unab dtvarignore : `dtvar'
					if _rc==0 {
						gen origvalue=value
						replace value=string(clock(value,"DMYhms",2025),"%25.0g") if strmatch(fieldname,"`dtvar'")
						* allow for cases where seconds haven't been specified
						replace value=string(clock(origvalue,"DMYhm",2025),"%25.0g") if strmatch(fieldname,"`dtvar'") & value=="." & origvalue~="."
						drop origvalue
					}
				}
			}
			if "`date_fields`i''" ~= "" {
				foreach dtvar in `date_fields`i'' {
					* skip fields that aren't yet in the data
					cap unab dtvarignore : `dtvar'
					if _rc==0 {
						replace value=string(clock(value,"DMY",2025),"%25.0g") if strmatch(fieldname,"`dtvar'")
					}
				}
			}
		}

		* write out a temp file with the commands necessary to apply each correction
		tempfile tempdo
		file open dofile using "`tempdo'", write replace
		local N = _N
		forvalues i = 1/`N' {
			local fieldnameval=fieldname[`i']
			local valueval=value[`i']
			local keyval=key[`i']
			local rownumval=rownum[`i']
			file write dofile `"cap replace `fieldnameval'="`valueval'" if key=="`keyval'""' _n
			file write dofile `"if _rc ~= 0 {"' _n
			if "`valueval'" == "" {
				file write dofile _tab `"cap replace `fieldnameval'=. if key=="`keyval'""' _n
			}
			else {
				file write dofile _tab `"cap replace `fieldnameval'=`valueval' if key=="`keyval'""' _n
			}
			file write dofile _tab `"if _rc ~= 0 {"' _n
			file write dofile _tab _tab `"disp"' _n
			file write dofile _tab _tab `"disp "CAN'T APPLY CORRECTION IN ROW #`rownumval'""' _n
			file write dofile _tab _tab `"disp"' _n
			file write dofile _tab `"}"' _n
			file write dofile `"}"' _n
		}
		file close dofile
	
		* restore primary data
		restore
		
		* execute the .do file to actually apply all corrections
		do "`tempdo'"

		* re-save data
		save "`dtafile'", replace
	}
	else {
		* restore primary data		
		restore
	}

	disp
	disp "Finished applying corrections in: `corrfile'"
	disp
}
