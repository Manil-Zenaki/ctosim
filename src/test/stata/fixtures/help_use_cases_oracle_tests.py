"""Positive/negative checks of the test helper, independent of production help."""
import copy
import json
import sys
import unittest
from pathlib import Path
from help_use_cases_checks import CHECKS, FIELD, TOP, Help, configuration_object, json_blocks


SOURCE = Path(__file__).with_name("help-use-cases-valid.sthlp").read_text(encoding="utf-8")


class HelpOracleTests(unittest.TestCase):
    def rejects(self, area, old, new):
        self.assertIn(old, SOURCE)
        with self.assertRaises((AssertionError, ValueError)):
            CHECKS[area](Help(SOURCE.replace(old, new)))

    def test_all_areas_accept_complete_help(self):
        for name, check in CHECKS.items():
            with self.subTest(area=name):
                check(Help(SOURCE))

    def test_smcl_styles_wrapping_and_literal_json_are_not_prescribed(self):
        variants = [SOURCE.replace("{dlgtab:", "{bf:"),
                    SOURCE.replace("{c -(}", "{c 123}").replace("{c )-}", "{c 125}"),
                    SOURCE.replace("{cmd:ctosim using", "{cmd:ctosim ///\n using"),
                    SOURCE.replace('"lognormal"', '"normal"')]
        # Literal JSON braces are allowed outside SMCL command loop delimiters.
        start, end = SOURCE.index('{c -(}\n'), SOURCE.index('{pstd}Use this configuration')
        variants.append(SOURCE[:start] + SOURCE[start:end].replace('{c -(}', '{').replace('{c )-}', '}') + SOURCE[end:])
        for i, variant in enumerate(variants):
            for name, check in CHECKS.items():
                with self.subTest(variant=i, area=name):
                    check(Help(variant))

    def test_json_can_be_shown_outside_examples(self):
        start, end = SOURCE.index('{c -(}\n'), SOURCE.index('{pstd}Use this configuration')
        block = SOURCE[start:end]
        variant = SOURCE[:start] + SOURCE[end:] + '\n{pstd}realistic.json contains:{p_end}\n' + block
        for name, check in CHECKS.items():
            with self.subTest(area=name):
                check(Help(variant))

    def test_each_required_basic_command_feature_is_enforced(self):
        for old, new in [('n(50)', ''), ('seed(42)', ''), ('seed(42) clear', 'seed(42)'),
                         ('dofile("import.do")', ''), ('replace clear', 'clear'),
                         ('saving("survey output")', ''), ('media("attached files")', ''),
                         ('{cmd:return list}', 'return list'), ('{cmd:display r(N)}', 'r(N)'),
                         ('{cmd:display r(warnings)}', 'r(warnings)'),
                         ('{cmd:type "`r(report)\'"}', 'type "`r(report)\'"')]:
            with self.subTest(feature=old):
                self.rejects('basic', old, new)

    def test_examples_cannot_be_borrowed_from_configuration(self):
        self.rejects('basic', '{dlgtab:Basic examples}', '{title:Configuration}\n{dlgtab:Basic examples}')

    def test_structure_needs_both_titles_and_preceding_prose(self):
        for old in ['{dlgtab:Basic examples}', '{dlgtab:Advanced examples}',
                    'Simulate a form and import plain data.']:
            with self.subTest(removed=old):
                self.rejects('structure', old, '')

    def test_advanced_requires_each_configuration_feature(self):
        # Rename one legal feature at a time in an otherwise complete example.
        for old, new in [('"profiles"', '"omitted_profiles"'), ('"area"', '"omitted_area"'),
                         ('"textPools"', '"omitted_textPools"'), ('"expr"', '"omitted_expr"'),
                         ('"distribution"', '"omitted_distribution"'),
                         ('"when"', '"omitted_when"'), ('"if"', '"omitted_if"'),
                         ('"weights"', '"omitted_weights"'), ('"value"', '"omitted_value"'),
                         ('"missingRate"', '"omitted_missingRate"'), ('"min"', '"omitted_min"')]:
            with self.subTest(feature=old):
                self.rejects('advanced', old, new)

    def test_loop_must_use_several_seeds_and_assert_must_check_an_answer(self):
        for old, new in [('seed(`s\')', 'seed(42)'), ('1/3', '1/1'),
                         ('assert inrange(age, 18, 65)', 'assert _N == 50'),
                         ('assert inrange(age, 18, 65)', 'assert r(N) == 50'),
                         ('assert inrange(age, 18, 65)', 'assert c(N) == 50'),
                         ('assert inrange(age, 18, 65)', 'summarize age')]:
            with self.subTest(feature=old):
                self.rejects('advanced', old, new)

    def test_advanced_rejects_omitted_features_in_otherwise_valid_json(self):
        original = json_blocks(Help(SOURCE).advanced())[0][2]
        paths = [('profiles',), ('area',), ('textPools',), ('fields', 'consent'),
                 ('fields', 'age'), ('fields', 'age', 'missingRate'),
                 ('fields', 'region'), ('fields', 'income'),
                 ('fields', 'income', 'when'), ('fields', 'annual_income')]
        start, end = SOURCE.index('{c -(}\n'), SOURCE.index('{pstd}Use this configuration')
        for path in paths:
            obj = copy.deepcopy(original)
            parent = obj
            for key in path[:-1]:
                parent = parent[key]
            del parent[path[-1]]
            configuration_object(obj)
            variant = SOURCE[:start] + json.dumps(obj) + '\n' + SOURCE[end:]
            with self.subTest(removed=path), self.assertRaises(AssertionError):
                CHECKS['advanced'](Help(variant))

    def test_foreach_seed_loop_is_also_accepted(self):
        CHECKS['advanced'](Help(SOURCE.replace('forvalues s = 1/3', 'foreach s in 7 19 53')))

    def test_constraint_assertion_accepts_optional_stata_prompt(self):
        for prefix in ('', '. ', '  .   '):
            with self.subTest(prefix=prefix):
                CHECKS['advanced'](Help(SOURCE.replace(
                    '{cmd:assert ', '{cmd:' + prefix + 'assert ')))

    def test_prompt_does_not_make_non_answer_assertions_valid(self):
        for command in ('assert _N == 50', 'assert r(N) == 50',
                        'assert c(N) == 50', 'summarize age'):
            with self.subTest(command=command):
                self.rejects('advanced', 'assert inrange(age, 18, 65)',
                             '. ' + command)

    def test_invalid_and_truncated_json_are_rejected(self):
        for old, new in [('"rural": 3', '"rural": 3, "rural": 4'),
                         ('"rural": 3', '"rural": null'), ('"rural": 3', '"rural": NaN'),
                         ('"rural": 3', '"rural": ...'),
                         ('"mean": 1000', '"meen": 1000'),
                         ('"sd": 400', '"sd": -1'), ('"min": 18', '"min": 80'),
                         ('"if":', '"when":'), ('"expr": "${income} * 12"', '"expr":'),
                         ('config("realistic.json")', 'config("realistic.json") config("absent.json")')]:
            with self.subTest(change=new):
                self.rejects('json', old, new)

    def test_every_documented_key_requires_a_description(self):
        for key in TOP | FIELD | {'if'}:
            # Remove its complete documentation paragraph, preserving examples.
            lines = SOURCE.splitlines()
            variant = '\n'.join(line for line in lines if not
                                (line.startswith('{pstd}{cmd:') and '{cmd:' + key + '}' in line))
            with self.subTest(key=key), self.assertRaises(AssertionError):
                CHECKS['configuration'](Help(variant))

    def test_generation_guarantees_and_each_automatic_text_name_are_required(self):
        for old in ['always', 'when it can be satisfied', 'without any setting',
                    'resp_name', 'phone_number', 'email', 'other_specify']:
            with self.subTest(removed=old):
                self.rejects('configuration', old, '')

    def test_schema_accepts_arbitrary_names_at_non_schema_levels(self):
        obj = {'profiles': {'distribution': 1}, 'fields': {
            'profiles': {'weights': {'unknownChoice': 1}},
            'expr': {'texts': ['Amina', 'Mojo']},
            'birthday': {'min': '1950-01-01', 'max': '2008-12-31'}}}
        configuration_object(obj)
        wrong = copy.deepcopy(obj)
        wrong['fields']['expr']['unknownSetting'] = 1
        with self.assertRaises(AssertionError):
            configuration_object(wrong)


if __name__ == '__main__':
    unittest.main(testRunner=unittest.TextTestRunner(stream=sys.stdout))
