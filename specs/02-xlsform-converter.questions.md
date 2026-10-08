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
