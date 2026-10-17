# Spec 02 test questions and assumptions

These tests were derived from `02-xlsform-converter.md` without reading the
production implementation. Workbook fixtures are generated with `openpyxl` in
pytest temporary directories; cached formula values are inserted into the test
workbook's OOXML. No persistent workbook fixtures or published forms are needed.

## 1. Original type text versus whitespace normalization

- **Question:** Does the catalog's type "as written" retain original whitespace,
  despite the instruction to trim and collapse type whitespace?
- **Assumption:** The catalog retains the SurveyCTO type after the specified
  whitespace normalization, rather than its replacement ODK type. Warnings for
  normalized inputs must identify that type; warning whitespace for irregular
  source text is not asserted.
- **Tests:** `test_type_whitespace_is_trimmed_and_collapsed`,
  `test_surveycto_metadata_types_become_empty_calculates`.

## 2. What constitutes an omitted block?

- **Question:** Does an individually disabled question count as a block, and do
  independently disabled descendants of an omitted group add warnings?
- **Assumption:** A single omitted row emits one warning. An omitted outer group
  or repeat emits one warning for the entire block, with no descendant warnings
  (including type-conversion warnings). Only an exact trimmed, case-insensitive
  `yes` disables a row.
- **Tests:** `test_disabled_yes_omits_a_single_row_and_warns`,
  `test_disabled_block_skips_through_its_matching_end`,
  `test_only_yes_disables_a_row`,
  `test_converted_and_disabled_warnings_follow_sheet_order`.

## 3. Malformed disabled blocks and disabled closing rows

- **Question:** Must structural validation happen before disabled-row removal?
  What happens when a disabled opener has no matching closer, or an end row
  itself has `disabled=yes`?
- **Assumption:** No precedence is inferred. Structural-error tests use enabled
  rows, and disabled-block tests use balanced structures. An ordinary disabled
  question with an otherwise unsupported type is removed before conversion.
- **Tests:** `test_disabled_invalid_type_is_excluded_before_pyxform_conversion`;
  malformed disabled structures and disabled closing rows are intentionally
  untested.

## 4. Language suffixes on nontext columns

- **Question:** Although renaming explicitly includes `relevance:Amharic`, what
  runtime meaning should translated relevance or read-only expressions have?
- **Assumption:** No localized binding semantics are invented. Unsuffixed
  relevance/read-only aliases and both suffixed/unsuffixed message aliases are
  covered through observable XML. Rename behavior for language-suffixed
  relevance/read-only columns has no independently specified XML assertion.
- **Tests:** `test_surveycto_column_aliases_produce_runtime_bindings`,
  `test_unsuffixed_message_aliases_are_retained`,
  `test_translated_message_aliases_are_linked_to_the_correct_bind`.

## 5. Translation fallback and conflicting columns

- **Question:** How should missing translated cells be filled? What wins when
  the default language also has an explicit suffix, or when both an alias and
  its ODK equivalent occur? Are choices-sheet translations included by the same
  rule?
- **Assumption:** Tests require each explicitly named survey language, the
  unsuffixed default, and preservation of the supplied translated text. They do
  not prescribe missing-cell fallback, collision precedence, translation order,
  or choices-sheet translation behavior.
- **Tests:** `test_translations_include_every_language_and_unsuffixed_default`,
  `test_translation_language_names_are_case_sensitive_and_kept_as_written`.

## 6. Numeric literal grammar and expression formatting

- **Question:** Are `+1`, `.5`, `1.`, or scientific notation bare numeric literals
  under this rule? Does "nothing else changes" apply before or after pyxform's
  expansion of XLSForm references?
- **Assumption:** Tests cover only the stated optional minus sign and decimal
  part with digits on both sides. They preserve expression content, including
  inner whitespace, after XML decoding, while allowing trimming at the outside
  and pyxform's required expansion of `${q}` references. Numeric expressions
  such as `1 + 2` are not bare literals. Extended numeric spellings are untested.
- **Tests:** `test_numeric_selected_argument_is_quoted_in_every_expression_column`,
  `test_selected_rewriting_preserves_surroundings_literals_and_nonliteral_arguments`,
  `test_selected_rewrite_handles_xlsform_reference_syntax`.

## 7. Duplicate choice preservation and column collisions

- **Question:** Does accepting duplicate choices mean retaining all original
  entries? Which column wins if both `name` and `value` are present?
- **Assumption:** Duplicate entries and their order remain part of the form;
  silently deduplicating them would discard labels. Tests use either `name` or
  `value`, and do not assert precedence when both occur.
- **Tests:** `test_choices_accept_name_or_value_and_duplicates_within_a_list`,
  `test_choice_lists_remain_separate_when_values_overlap`.

## 8. Empty workbooks and settings

- **Question:** Is a survey with valid headers but zero fields a valid form?
  Are settings with headers only, no columns, or no form-id column equivalent to
  empty settings values? Do whitespace-only settings values count as empty?
- **Assumption:** Empty/missing settings values use the specified defaults,
  including a settings sheet without data. A completely empty survey is an
  error because the required headers are absent. Header-only surveys with valid
  headers, and whitespace-only settings values, have no success/failure
  expectation. Synthetic result records cover writing an empty field catalog.
- **Tests:** `test_empty_settings_produce_filename_defaults`,
  `test_completely_empty_survey_sheet_is_reported`,
  `test_catalog_represents_absent_version_as_null_and_empty_fields_as_array`.

## 9. File suffix versus workbook contents

- **Question:** Must a valid XLSX archive with a different filename extension be
  rejected, and is `.XLSX` accepted?
- **Assumption:** Tests use `.xlsx` for workbooks and test unreadable, missing,
  malformed, and nonworkbook inputs. Extension-only rejection is not asserted.
- **Tests:** The file-input cases in `test_converter_errors.py`; alternate
  extensions on input are intentionally untested.

## 10. Diagnostic wording, ambiguous rows, and exception causes

- **Question:** Must diagnostics contain a full input path, and which row should
  be named when two rows conflict? Does an error detected by explicit validation
  need an artificial underlying cause?
- **Assumption:** The filename plus a description and the relevant row number
  are sufficient; exact prose is not prescribed. Either conflicting row can
  identify duplicate names or mismatched boundaries. No artificial cause is
  required for preflight validation. A deliberately injected filesystem
  exception must remain in the cause chain. Unsupported types passed unchanged
  to pyxform must preserve the resulting underlying exception as a cause.
- **Tests:** `test_read_permission_failure_is_wrapped_and_kept_as_cause`,
  `test_unsupported_type_is_reported_and_sensor_prefixes_require_a_whole_word`,
  `test_duplicate_field_names_are_reported`, and structural-error tests.

## 11. Serialization details not specified by the JSON example

- **Question:** Must non-ASCII JSON characters be literal or escaped, and must
  file newlines be LF even on Windows?
- **Assumption:** Both Unicode escaping styles and LF/CRLF are accepted. UTF-8
  decoding, object/field key order, two-space indentation, the terminal newline,
  exact catalog content, and XML text preservation are asserted. Warnings are
  excluded from the catalog because they are absent from its specified schema.
- **Tests:** All `write` and successful CLI tests in `test_converter_output.py`.

## 12. Write-path edge cases and failures

- **Question:** What should happen with a path lacking an extension, missing
  parent directories, a catalog path equal to the XML path, or write failures?
- **Assumption:** Replacing an absent extension appends `.fields.json`; replacing
  an existing extension replaces only its final suffix. Ordinary strings and
  `Path` values are accepted. Parent directories exist in all tests. No directory
  creation policy, collision policy, or output-I/O exception type is invented.
- **Tests:** `test_write_uses_default_catalog_path_and_exact_xml`,
  `test_write_accepts_explicit_catalog_path`.

## 13. CLI failure with preexisting outputs

- **Question:** Does "writes no file" also prohibit truncating or deleting
  existing outputs when conversion fails? Is stdout constrained?
- **Assumption:** Existing outputs remain byte-for-byte intact on a conversion
  error. The CLI emits warnings as individual stderr lines, and emits the API
  error message without a traceback. No success stdout format is prescribed.
- **Tests:** `test_cli_conversion_failure_preserves_existing_output_files`,
  `test_cli_conversion_error_prints_message_exits_one_and_writes_no_file`,
  `test_cli_prints_each_warning_on_its_own_stderr_line_in_order`.

## 14. Module 01 integration from pytest

- **Question:** Which public Python-to-Java entry point should pytest use to
  prove that the generated XML loads with module 01's `FormLoader`?
- **Assumption:** Spec 02 provides no such bridge. These Python unit tests parse
  the complete XML and check the primary instance, bindings, repeat template
  paths, controls, and translations. They do not claim to verify actual Java
  `FormLoader` acceptance or inspect Java production code.
- **Tests:** `test_output_is_complete_xform_with_data_primary_root`,
  `test_catalog_follows_sheet_order_including_groups_repeats_and_metadata`,
  and XML assertions throughout `test_converter_normalization.py`.

## 15. Size and statistical requirements

- **Question:** Is there a workbook-size limit or a probabilistic behavior to
  test for this module?
- **Assumption:** None is stated. Fixtures stay small and focused; determinism
  is tested by repeating conversion of the same workbook. No invented capacity
  limit or statistical distribution is imposed.
- **Tests:** `test_conversion_is_deterministic_and_never_modifies_input`.

## Amendment review

The spec's Clarifications confirm points 1-13 and 15 above and place the
Java-loader check in a later integration suite. Those decisions are retained.
The current suite also incorporates Amendments 1-3. In particular, the two
older assertions that expected a `calculate` attribute on `calculate_here`
now expect `{urn:ctosim:xforms}calculate-here` instead. No production code or
public stub bodies were read while updating the tests.

## 16. Namespace settings syntax and prefix collisions

- **Question:** What column and value syntax declares the form's own namespaces
  in settings? What happens if settings already assign `ctosim` to another URI?
- **Assumption:** The required `ctosim` prefix must be declared on the document
  root and resolve to `urn:ctosim:xforms`. The spec does not define the input
  syntax for additional namespace declarations or collision precedence; no
  guessed settings syntax or collision policy is tested. Preservation of
  user-declared namespaces remains an explicit coverage gap pending that syntax.
- **Tests:** `test_calculate_here_declares_the_required_prefix_on_the_root`,
  `test_form_without_calculate_here_has_no_custom_attribute_or_namespace`.

## 17. Empty and disabled calculate_here rows

- **Question:** Must an empty-calculation row still add the namespace? Does a
  disabled `calculate_here` count as a row that requires that declaration?
- **Assumption:** An enabled row with no calculation is still a read-only text
  question, so it declares `ctosim` but has neither calculation attribute.
  Missing columns, empty cells, and uncached formulas all supply no calculation.
  A disabled row is removed before conversion, produces only its omission
  warning, and does not introduce the namespace. Whitespace-only calculations
  are not assigned an empty/nonempty interpretation.
- **Tests:** `test_empty_or_uncached_calculation_is_an_empty_read_only_question`,
  `test_calculate_here_without_a_calculation_column_is_an_empty_question`,
  `test_disabled_calculate_here_adds_only_an_omission_warning`.

## 18. calculate_here reference expansion and discarded columns

- **Question:** What formatting and relative/absolute paths should its custom
  expression use inside groups/repeats? What happens with translated labels and
  hints when the row's label and hint columns are discarded?
- **Assumption:** A normal `calculate` in the same scope is the public-output
  reference for pyxform's expansion, as Amendment 2 explicitly requires.
  Tests also independently check numeric quoting and absence of `${...}`.
  Unsuffixed discarded columns must not change the result; changing `label`
  cannot replace the field-name label. Translated-column removal and translated
  name-label fallback remain untested because their interaction with the
  global translation rule is not explicit.
- **Tests:** `test_calculate_here_expands_references_exactly_like_a_normal_calculate`,
  `test_calculate_here_discards_each_other_column`,
  `test_calculate_here_source_label_does_not_override_the_name_label`,
  `test_calculate_here_relevance_is_retained_and_rewritten`.

## 19. Row-2 settings versus ignoring empty rows

- **Question:** If settings row 2 is completely blank and row 3 contains values,
  does ignoring empty rows promote row 3 into the settings record?
- **Assumption:** The explicit requirement to read settings from row 2 takes
  precedence. The converter falls back to the filename/default values rather
  than selecting a later physical row.
- **Tests:** `test_blank_settings_row_two_does_not_promote_row_three`.

## 20. Translated secondary choice labels

- **Question:** What XML tag contains a secondary item's translation reference?
- **Assumption:** Amendment 1 requires retaining the label or its itext reference
  but does not name the reference child. Tests locate the corresponding
  translation ID in the item's text/attributes without imposing a child tag.
  The fixture declares both languages on the survey as well as the choices
  sheet, so it does not decide the earlier open question about languages found
  exclusively on the choices sheet. Missing translations and duplicate values
  combined with translations remain unspecified.
- **Tests:** `test_translated_choices_keep_separate_secondary_items_and_label_references`.

## 21. Amendment 3 supersedes the ignored minimum_seconds column

- **Question:** Does the earlier rule ignoring `minimum_seconds` still apply,
  and is the timing information limited to visible questions?
- **Assumption:** Amendment 3 takes precedence. Every enabled survey row that
  produces a node can carry its own minimum-seconds bind attribute, including
  metadata, calculates, notes, and group/repeat openers. No inherited timing is
  inferred for children or siblings. The obsolete `minimum_seconds` case was
  removed from `test_nonruntime_columns_have_no_effect`. Timing cells on end
  rows or otherwise node-less rows are not tested: no bind target is specified.
- **Tests:** `test_minimum_seconds_is_a_namespaced_bind_attribute`,
  `test_minimum_seconds_applies_to_each_node_producing_row`,
  `test_minimum_seconds_on_container_and_child_uses_their_own_paths`.

## 22. Timing numeric grammar and formatting

- **Question:** Does numeric text retain leading/trailing zeros as version text
  does? Are exponent notation, signed zero, nonfinite values, booleans, and
  locale-specific numbers accepted? Is numeric text with surrounding whitespace
  serialized trimmed or as originally written?
- **Assumption:** Only ordinary nonnegative Excel numbers and unambiguous numeric
  strings (`0`, `10`, `2.5`) are asserted. Whole numeric cells have no decimal
  part; fractional values retain their fraction. Blank/whitespace-only cells
  produce no attribute. Negative values and clearly nonnumeric text/expressions
  are rejected. No precision limit or additional numeric grammar is invented;
  the other spellings above are intentionally not assigned expectations.
- **Tests:** `test_minimum_seconds_is_a_namespaced_bind_attribute`,
  `test_empty_or_uncached_minimum_seconds_changes_nothing`,
  `test_invalid_minimum_seconds_raises_conversion_error_with_file_and_physical_row`,
  `test_minimum_seconds_uses_cached_formula_value`,
  `test_invalid_cached_minimum_seconds_is_rejected`.

## 23. Invalid timing in disabled rows

- **Question:** Is an invalid minimum_seconds cell in a disabled row validated
  before that row or its enclosing block is removed?
- **Assumption:** The spec explicitly removes disabled timing attributes but
  does not explicitly settle invalid-value validation precedence. Disabled
  fixtures therefore use valid numeric minima, including nested speed fields;
  tests do not require either accepting or rejecting invalid disabled minima.
- **Tests:** `test_disabled_timing_and_speed_fields_leave_no_custom_attributes`.

## 24. Namespace rule versus an empty calculate_here

- **Question:** Amendment 3 says the namespace is declared only when a custom
  attribute exists, but the confirmed Amendment 2 clarification requires it for
  an enabled calculate_here with no calculation. Which takes precedence there?
- **Assumption:** Retain the explicitly confirmed empty-calculate_here behavior
  (point 17), since Amendment 3 also says everything else is unchanged. For
  forms without calculate_here, any enabled minimum or speed attribute requires
  the root `ctosim` declaration, and empty/disabled timing or ordinary audit
  fields alone do not. An empty calculate_here with a minimum has the namespace
  under either reading. Existing tests for empty calculate_here are retained;
  the apparent textual conflict remains recorded for clarification.
- **Tests:** `test_empty_or_uncached_calculation_is_an_empty_read_only_question`,
  `test_minimum_seconds_is_a_namespaced_bind_attribute`,
  `test_speed_violation_fields_are_tagged_empty_hidden_calculates`,
  `test_other_empty_calculate_types_do_not_acquire_speed_attributes`,
  `test_timed_empty_calculate_here_keeps_minimum_and_readonly_attributes`,
  `test_namespace_does_not_leak_between_conversions`.

## 25. Warnings, serialization, and cross-amendment behavior

- **Question:** Does a timing attribute add warnings or new catalog keys, and
  can the new attributes coexist with calculate_here on the same row?
- **Assumption:** Amendment 3 changes only the specified XML attributes and
  numeric validation. Minima create no new warnings or catalog entries. Speed
  types retain their original type and one conversion warning per row. A minimum
  can coexist with either calculate_here or a speed tag. Speed audit remains
  an ordinary empty calculate without a speed tag. The existing JSON schema,
  CLI behavior, deterministic output, and input immutability still apply.
- **Tests:** `test_minimum_seconds_adds_no_warning_or_catalog_entry`,
  `test_all_custom_attributes_coexist_without_changing_catalog_or_warning_order`,
  `test_custom_attributes_are_deterministic_and_do_not_modify_input`,
  `test_cli_writes_timing_attributes_and_original_speed_types`,
  `test_cli_invalid_minimum_seconds_preserves_existing_outputs`, and the
  `invalid_minimum` case of
  `test_cli_conversion_error_prints_message_exits_one_and_writes_no_file`.

## 26. Amendment 4: equivalence after inserting skipped rows

- **Question:** Does "converts the same" require identical result objects when
  inserting empty or disabled rows changes physical row numbers and warnings?
- **Assumption:** With the same filename and settings, the XForm text and form
  metadata stay identical. The catalog retains the same names, types, paths,
  and order, but its row numbers refer to the padded sheet. Each independently
  disabled row or outer disabled block adds its specified warning; empty rows
  add none. Warning wording is not prescribed. Nested disabled descendants
  produce no additional warnings, consistent with confirmed point 2.
- **Tests:** `test_long_row_runs_anywhere_preserve_the_complete_form`,
  `test_long_omitted_blocks_anywhere_leave_later_fields_intact`,
  `test_long_runs_preserve_enclosing_container_and_sibling_paths`,
  `test_long_disabled_block_skips_nested_blocks_until_its_own_closing_row`,
  `test_multiple_long_runs_preserve_warning_order_and_physical_rows` in the new
  `converter/tests/test_converter_long_row_runs.py`.

## 27. Amendment 4: size examples and the reported real form

- **Question:** Are 100 and 1,000 limits, and does the reported 1,448-row form
  count the header? Does a 136-row disabled group include its boundaries?
- **Assumption:** The stated lengths are examples, not limits or performance
  guarantees. Tests exercise 99/100/101 and 999/1,000/1,001 single-row omissions,
  plus one-row controls, and 100/136/1,000-row block and mixed gaps. The synthetic
  reproduction has 1,448 physical rows including its header and a 136-row
  disabled group including its opener and closer. No claim is made to reproduce
  the unavailable real workbook's exact layout. No timing threshold is imposed.
- **Tests:** The first three parameterized tests named in point 26 and
  `test_1448_row_survey_with_136_row_disabled_group_keeps_every_later_row`.

## 28. Amendment 4: empty cells and other sheets

- **Question:** Does the amendment cover empty choice rows and formula-only
  rows, and may it change the special rule for settings row 2?
- **Assumption:** The existing input rules still apply: uncached formulas count
  as empty cells, and completely empty rows on the choices sheet are ignored.
  Long runs therefore cannot truncate later survey fields or choice entries.
  No new interpretation of whitespace-only cells, partially populated rows,
  or disabled flags on choices is introduced. Settings still come from physical
  row 2, as confirmed in point 19; there is no new settings behavior to test.
- **Tests:** `test_long_run_of_uncached_formula_rows_counts_as_empty`,
  `test_long_empty_choice_runs_do_not_truncate_choices_or_drop_duplicates`.

## 29. Amendment 4: errors beyond gaps and disabled validation precedence

- **Question:** Does ignoring arbitrarily long gaps hide later errors, or settle
  validation precedence for malformed or invalid disabled content?
- **Assumption:** Enabled errors beyond a gap still raise ConversionError naming
  the original survey row and input filename. An unclosed enabled group before
  a long blank tail still fails. Existing ambiguities about malformed disabled
  structures and invalid disabled timing values (points 3 and 23) remain open;
  new disabled blocks are balanced and do not contain invalid minima. Diagnostic
  prose remains flexible, and underlying pyxform exceptions retain their cause.
- **Tests:** `test_errors_after_long_runs_report_the_original_survey_row`,
  `test_unclosed_group_before_a_long_empty_tail_is_still_an_error`.
