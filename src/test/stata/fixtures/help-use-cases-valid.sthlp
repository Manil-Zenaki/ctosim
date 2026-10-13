{smcl}
{* Test-only positive oracle fixture; not the command's documentation.}
{title:Examples}
{dlgtab:Basic examples}
{pstd}Simulate a form and import plain data.
{p_end}
{phang}{cmd:ctosim using "survey.xlsx", n(50) seed(42) clear}{p_end}
{pstd}Load the output with the SurveyCTO import template.
{phang}{cmd:ctosim using "survey.xlsx", dofile("import.do") replace clear}{p_end}
{pstd}Choose the directory for the output.
{phang}{cmd:ctosim using "survey.xlsx", saving("survey output") clear}{p_end}
{pstd}Supply the directory containing attached CSV files.
{phang}{cmd:ctosim using "survey.xlsx", media("attached files") clear}{p_end}
{pstd}Read the results and open the run report.
{phang}{cmd:return list}{p_end}
{phang}{cmd:display r(N)}{p_end}
{phang}{cmd:display r(warnings)}{p_end}
{phang}{cmd:type "`r(report)'"}{p_end}
{dlgtab:Advanced examples}
{pstd}Save these settings as realistic.json to fix consent, bound age, weight choices,
change missingness and create realistic households.
{p_end}
{c -(}
  "profiles": {c -(}"rural": 3, "urban": 2{c )-},
  "area": {c -(}"minLatitude": 8, "maxLatitude": 9, "minLongitude": 38, "maxLongitude": 39{c )-},
  "textPools": {c -(}"firstName": ["Amina", "Kebede"], "comment": ["Available after work"]{c )-},
  "fields": {c -(}
    "consent": {c -(}"value": "1"{c )-},
    "age": {c -(}"min": 18, "max": 65, "missingRate": 0{c )-},
    "region": {c -(}"weights": {c -(}"1": 2, "2": 1{c )-}{c )-},
    "income": {c -(}"distribution": {c -(}"lognormal": {c -(}"mean": 1000, "sd": 400{c )-}{c )-},
      "when": [{c -(}"if": "profile() = 'rural'", "distribution": {c -(}"normal": {c -(}"mean": 500, "sd": 100{c )-}{c )-}{c )-}]{c )-},
    "annual_income": {c -(}"expr": "${income} * 12"{c )-}
  {c )-}
{c )-}
{pstd}Use this configuration file to simulate households.
{phang}{cmd:ctosim using "survey.xlsx", config("realistic.json") clear}{p_end}
{pstd}Test analysis code on several samples by varying the seed.
{phang}{cmd:forvalues s = 1/3 {c -(}}{p_end}
{phang}{cmd:    ctosim using "survey.xlsx", seed(`s') saving("sample `s'") clear}{p_end}
{phang}{cmd:    summarize age}{p_end}
{phang}{cmd:{c )-}}{p_end}
{pstd}Check that age satisfies the form's adult-age constraint.
{phang}{cmd:assert inrange(age, 18, 65)}{p_end}
{title:Configuration}
{pstd}{cmd:seed}: Sets the seed of all randomness.{p_end}
{pstd}{cmd:submissions}: Sets how many submissions to simulate.{p_end}
{pstd}{cmd:start}: Sets the starting local date and time of fieldwork.{p_end}
{pstd}{cmd:fieldworkDays}: Spreads submissions across this many days.{p_end}
{pstd}{cmd:missingRate}: Controls optional missing answers globally or for a field.{p_end}
{pstd}{cmd:specialRate}: Controls selection of special choice codes.{p_end}
{pstd}{cmd:specialValues}: Lists the special refusal and unknown codes.{p_end}
{pstd}{cmd:repeats}: Bounds the number of enumerator-controlled repeat instances.{p_end}
{pstd}{cmd:integerRange}: Sets default bounds for integer questions.{p_end}
{pstd}{cmd:decimalRange}: Sets default bounds for decimal questions.{p_end}
{pstd}{cmd:fields}: Maps question names to their individual overrides.{p_end}
{pstd}{cmd:timeZone}: Sets the time zone of simulated devices.{p_end}
{pstd}{cmd:enumerators}: Sets the number of simulated enumerators.{p_end}
{pstd}{cmd:speedViolationRate}: Controls attempts to leave minimum-time fields early.{p_end}
{pstd}{cmd:profiles}: Weights the household profiles drawn per submission.{p_end}
{pstd}{cmd:area}: Bounds survey coordinates globally or for a field.{p_end}
{pstd}{cmd:textPools}: Replaces built-in pools of plausible text answers.{p_end}
{pstd}{cmd:value}: Fixes the answer to this value.{p_end}
{pstd}{cmd:min}, {cmd:max}: Bound numeric or date answers.{p_end}
{pstd}{cmd:weights}: Gives relative probabilities for the listed choices.{p_end}
{pstd}{cmd:texts}: Lists possible answers for a text question.{p_end}
{pstd}{cmd:distribution}: Draws numbers from normal or lognormal distributions.{p_end}
{pstd}{cmd:when}: Lists conditional overrides in their evaluation order.{p_end}
{pstd}{cmd:if}: Gives a rule's XPath condition for applying its settings.{p_end}
{pstd}{cmd:expr}: Computes an answer from an XPath expression.{p_end}
{pstd}Generated answers always satisfy the question's constraint when it can be satisfied.{p_end}
{pstd}Text questions named like resp_name, phone_number, email or other_specify
get plausible answers without any setting.{p_end}
