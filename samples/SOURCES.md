# Sample forms

Real SurveyCTO XLSForms used for integration tests. Only the form
definitions are versioned (`forms/`). Research data and archives are
downloaded into `raw/`, which is not versioned.

| File in `forms/` | Source | Licence | Notes |
|---|---|---|---|
| `ethiopia_migration_household.xlsx` | `questionnaire/hh.xlsx` in the replication package of *Are Estimates of Irregular Migration Intentions Biased? Evidence from a Double List Experiment in Ethiopia*, Zenodo record [15701795](https://zenodo.org/records/15701795) (`list_replication_files.7z`) | CC BY 4.0 | Three languages, two repeats, `index()`, `choice-label()`, `rank-value()`, `duration()`. Raw data: `data/dle.RDS`. |
| `ipa_household_survey.xlsx` | `sample/Household_Survey.xlsx` in [PovertyAction/surveycto-extractor](https://github.com/PovertyAction/surveycto-extractor) | MIT (`LICENSE-ipa-surveycto-extractor.txt`) | `pulldata()`, choice filters, `jr:choice-name()`, `comments`. Fictional training data: `sample/household_survey.dta`. |
| `ethiopia_mnh_household.xlsx` | Additional file 1 (MOESM1) of *Effective coverage of maternal and newborn health services ... in Ethiopia*, Reproductive Health, [doi:10.1186/s12978-025-02241-5](https://doi.org/10.1186/s12978-025-02241-5) | CC BY 4.0 | As published, choice list `region` is missing. Data: MOESM5 (CSV). |
| `ethiopia_mnh_facility.xlsx` | Additional file 2 (MOESM2), same article | CC BY 4.0 | No settings sheet. Data: MOESM6. |
| `ethiopia_mnh_exit_interview.xlsx` | Additional file 3 (MOESM3), same article | CC BY 4.0 | As published, has an unmatched `end group` (row 186). Data: MOESM7. |

## Re-downloading the raw data

```
curl -L -o raw/ethiopia-migration/list_replication_files.7z "https://zenodo.org/api/records/15701795/files/list_replication_files.7z/content"
curl -L -o raw/ipa-household/household_survey.dta "https://raw.githubusercontent.com/PovertyAction/surveycto-extractor/main/sample/household_survey.dta"
curl -L -A "Mozilla/5.0" -o raw/ethiopia-mnh/MOESM5_ESM.csv "https://static-content.springer.com/esm/art%3A10.1186%2Fs12978-025-02241-5/MediaObjects/12978_2025_2241_MOESM5_ESM.csv"
```

## Converted XForms

`xforms/` holds the XForms produced from the forms above by the converter
(module 02), with their field catalogs, so the Java modules can be tested
on real forms without running Python. Regenerate with:

```
for b in ethiopia_migration_household ipa_household_survey ethiopia_mnh_facility; do
  .venv/Scripts/python -m ctosim_xlsform samples/forms/$b.xlsx -o samples/xforms/$b.xml
done
```

The two other forms are not converted: as published they are invalid (see
the notes above), and the converter rejects them with the offending row.
