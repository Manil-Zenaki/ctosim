{smcl}
{* *! version 0.1.0}{...}
{title:Title}

{phang}
{bf:ctosim} {hline 2} Simulate submissions of a SurveyCTO form and load them


{title:Syntax}

{p 8 15 2}
{cmd:ctosim} {cmd:using} {it:form}
[{cmd:,} {it:options}]

{pstd}
{it:form} is a SurveyCTO form definition: an XLSForm ({cmd:.xlsx}), or an
XForm already converted ({cmd:.xml}) with its {cmd:.fields.json} file beside it.

{synoptset 22 tabbed}{...}
{synopthdr}
{synoptline}
{synopt:{opt n(#)}}number of submissions, 1 to 1,000,000; default: the configuration's (100){p_end}
{synopt:{opt seed(#)}}seed; default: the configuration's (1){p_end}
{synopt:{opt config(filename)}}simulation configuration (JSON){p_end}
{synopt:{opt saving(dirname)}}output directory; default {cmd:ctosim-out/}{it:form name}{p_end}
{synopt:{opt media(dirname)}}attached files read by {cmd:pulldata()}; default: the form's directory{p_end}
{synopt:{opt server(name)}}server name in attachment URLs; default {cmd:example}{p_end}
{synopt:{opt dofile(filename)}}load the data with this SurveyCTO import do-file{p_end}
{synopt:{opt replace}}allow writing into an output directory that already holds a run{p_end}
{synopt:{opt clear}}allow replacing the data in memory{p_end}
{synopt:{opt fakepreload}}invent the preloaded files the form reads but {opt media()} lacks; see {help ctosim##fakepreload:Fictitious preload}{p_end}
{synopt:{opt id(name)}}the question holding the survey id (household, respondent): each submission gets its own value; see {help ctosim##fieldwork:Fieldwork plan}{p_end}
{synopt:{opt problems}}inject fieldwork problems on purpose and list them in an answer key; see {help ctosim##problems:Injected problems}{p_end}
{synoptline}


{title:Description}

{pstd}
{cmd:ctosim} generates synthetic submissions for a SurveyCTO form. The
submissions follow the form's own logic (relevance, constraints, required
fields, calculations, repeat groups, choice filters) and are written in
SurveyCTO's export layout, so that cleaning and analysis code can be written
and tested before fieldwork. The same form, options and configuration always
give the same data.

{pstd}
The output directory receives the wide CSV export, the long CSV exports (one
file per repeat group), the converted form, and {cmd:run-report.txt}, which
lists every warning of the run. {cmd:ctosim} then loads the data in one of two
ways:

{phang2}
{it:Plain import} (default): the wide CSV is imported with
{cmd:import delimited}, {cmd:varnames(1)}, UTF-8 and strict quotes.
Dates are text and no labels are applied.

{phang2}
{it:SurveyCTO import do-file} ({opt dofile()}): the do-file that SurveyCTO
generates for the form ({it:Export} {hline 1} Stata template) is run in the
output directory, where it finds the CSV under the name it expects. The
simulated data then arrive exactly as the real data will, with converted
dates, labels and text fields.


{title:Installation}

{pstd}
Install or reinstall with{p_end}
{phang2}{cmd:. net install ctosim, from("https://raw.githubusercontent.com/Manil-Zenaki/ctosim/stata-package/") replace}{p_end}
{pstd}
and update with {cmd:ado update ctosim, update}.


{title:Requirements}

{phang}
{bf:Java 17 or newer}. Stata 18 and later include one. With Stata 16 or 17,
install a free Java 17 (for example Eclipse Temurin, {browse "https://adoptium.net"}),
or set the environment variable {cmd:CTOSIM_JAVA} to its {cmd:java} executable.
{cmd:ctosim} uses, in order: {cmd:CTOSIM_JAVA}; Stata's own Java
({cmd:java query}); {cmd:JAVA_HOME}; {cmd:java} on the system path.

{phang}
{bf:Python 3.10 or newer}, only to convert an XLSForm: the Python set in Stata
({cmd:python query}; see {cmd:python search} and {cmd:python set exec}), or
the one named by {cmd:CTOSIM_PYTHON}. Nothing needs to be installed in it.

{pstd}
The environment variables {cmd:CTOSIM_JAR} and {cmd:CTOSIM_CONVERTER} can point
to another simulator ({cmd:ctosim.jar}) or converter ({cmd:ctosim_converter.jar}).


{title:Configuration}

{pstd}
The optional configuration is a JSON file passed with {opt config()}; every
key is optional. Generated answers always satisfy their question's
constraint when the constraint can be satisfied: ranges with "don't know"
codes, {cmd:if()}, {cmd:not()}, {cmd:selected()} and {cmd:count-selected()}
rules, lengths, {cmd:regex()} and any other expression (the simulator
searches for a value the constraint accepts). Text questions named like
{cmd:resp_name}, {cmd:phone_number}, {cmd:email} or {cmd:other_specify} get
plausible names, phone numbers, e-mail addresses and comments without any
setting.

{pstd}{bf:Top-level keys}{p_end}
{synoptset 22}{...}
{synopt:{cmd:seed}}seed of all randomness (default 1){p_end}
{synopt:{cmd:submissions}}number of submissions (default 100){p_end}
{synopt:{cmd:start}, {cmd:fieldworkDays}}start of fieldwork, which also sets when work starts each day, and its expected length (default 2026-01-05T08:00, 30 days){p_end}
{synopt:{cmd:timeZone}}time zone of stored date-times (default UTC){p_end}
{synopt:{cmd:enumerators}}number of simulated enumerators (default 5){p_end}
{synopt:{cmd:interviewsPerDay}}most interviews an enumerator does in a day (default 4); see {help ctosim##fieldwork:Fieldwork plan}{p_end}
{synopt:{cmd:id}}the question holding the survey id, as option {opt id()}{p_end}
{synopt:{cmd:problems}}rates of injected problems, as option {opt problems}; see {help ctosim##problems:Injected problems}{p_end}
{synopt:{cmd:missingRate}}probability that an optional question is left blank (default 0.05){p_end}
{synopt:{cmd:specialRate}, {cmd:specialValues}}probability of a "don't know / refused" code, and which values are such codes{p_end}
{synopt:{cmd:speedViolationRate}}probability of a speed violation on a field with a minimum time (default 0.1){p_end}
{synopt:{cmd:repeats}}number of instances of repeats without a count (default 1 to 3){p_end}
{synopt:{cmd:integerRange}, {cmd:decimalRange}}default bounds of numbers (0-100, 0-1000){p_end}
{synopt:{cmd:profiles}}household profiles; one is drawn per submission, in proportion to its weight{p_end}
{synopt:{cmd:area}}rectangle where GPS points are drawn ({cmd:minLatitude}, {cmd:maxLatitude}, {cmd:minLongitude}, {cmd:maxLongitude}){p_end}
{synopt:{cmd:textPools}}lists replacing the built-in {cmd:firstName}, {cmd:lastName}, {cmd:comment} texts, or adding {cmd:place} names{p_end}
{synopt:{cmd:fields}}settings for individual fields, by field name (below){p_end}

{pstd}{bf:Field settings} (under {cmd:fields}){p_end}
{synopt:{cmd:value}}always this answer{p_end}
{synopt:{cmd:min}, {cmd:max}}bounds of a number or a date{p_end}
{synopt:{cmd:weights}}relative weights of the choices of a select question{p_end}
{synopt:{cmd:texts}}answers to draw from for a text question{p_end}
{synopt:{cmd:missingRate}}this field's own rate of blank answers{p_end}
{synopt:{cmd:distribution}}{cmd:normal} or {cmd:lognormal}, with the {cmd:mean} and {cmd:sd} of the variable{p_end}
{synopt:{cmd:expr}}the answer is the value of an expression, written as in the form ({cmd:${c -(}field{c )-}}, {cmd:pulldata()}, {cmd:profile()}){p_end}
{synopt:{cmd:area}}this field's own area{p_end}
{synopt:{cmd:when}}rules tried in order: the first whose {cmd:if} expression is true replaces the settings it names{p_end}

{pstd}
Expressions ({cmd:if}, {cmd:expr}) are evaluated when the question is reached,
so they can use any earlier answer; {cmd:profile()} is the submission's
profile. Full rules: the project's developer guide and its specifications.


{title:Examples}

{pstd}{bf:Basic examples}{p_end}

{pstd}Simulate 300 submissions of a form and import them as plain data:{p_end}
{phang2}{cmd:. ctosim using "household.xlsx", n(300) seed(42) clear}{p_end}

{pstd}Load them with the import do-file SurveyCTO generates for the form, so
that they arrive exactly as the real data will (dates, labels):{p_end}
{phang2}{cmd:. ctosim using "household.xlsx", n(300) seed(42) dofile("import_household.do") replace clear}{p_end}

{pstd}Choose where the CSV exports and the run report are written:{p_end}
{phang2}{cmd:. ctosim using "household.xlsx", n(100) saving("simulated/round1") replace clear}{p_end}

{pstd}A form whose {it:pulldata()} or {it:search()} reads attached CSV files kept in another folder:{p_end}
{phang2}{cmd:. ctosim using "household.xlsx", n(100) media("attachments") clear}{p_end}

{pstd}Read the stored results and look at the warnings of the run:{p_end}
{phang2}{cmd:. ctosim using "household.xlsx", n(100) clear}{p_end}
{phang2}{cmd:. return list}{p_end}
{phang2}{cmd:. display r(N) " submissions, " r(warnings) " warnings, " r(unsatisfied) " unsatisfied constraints"}{p_end}
{phang2}{cmd:. type "`r(report)'"}{p_end}

{pstd}{bf:Advanced examples}{p_end}

{pstd}Fix the consent, restrict the age, weight the regions and make the
phone number often missing, with a configuration file {it:sim.json}:{p_end}

{asis}
        {
          "seed": 7,
          "fields": {
            "consent": {"value": "1"},
            "age": {"min": 15, "max": 64},
            "region": {"weights": {"1": 5, "2": 3, "3": 2}},
            "phone": {"missingRate": 0.5}
          }
        }
{smcl}
{phang2}{cmd:. ctosim using "household.xlsx", n(500) config("sim.json") clear}{p_end}

{pstd}Realistic data: rural and urban households, ages from a normal
distribution, incomes from a lognormal distribution that depends on the
profile, schooling that depends on the age and the profile, the number of
other members computed from the household size, GPS points in the survey
area and local place names, with {it:realistic.json}:{p_end}

{asis}
        {
          "profiles": {"rural": 3, "urban": 1},
          "area": {"minLatitude": 8.9, "maxLatitude": 9.1,
                   "minLongitude": 38.6, "maxLongitude": 38.9},
          "textPools": {"place": ["Adama", "Bishoftu", "Mojo"]},
          "fields": {
            "age": {"distribution": {"normal": {"mean": 32, "sd": 12}}, "min": 0, "max": 99},
            "income": {"distribution": {"lognormal": {"mean": 3000, "sd": 2000}},
                       "min": 0, "max": 100000,
                       "when": [{"if": "profile() = 'urban'",
                                 "distribution": {"lognormal": {"mean": 9000, "sd": 5000}}}]},
            "school": {"weights": {"0": 1, "1": 2, "2": 3, "3": 3},
                       "when": [{"if": "${age} < 6", "value": "0"},
                                {"if": "profile() = 'rural'",
                                 "weights": {"0": 4, "1": 4, "2": 2}}]},
            "hh_others": {"expr": "${hh_size} - 1"}
          }
        }
{smcl}
{phang2}{cmd:. ctosim using "household.xlsx", n(1000) seed(2026) config("realistic.json") dofile("import_household.do") replace clear}{p_end}
{phang2}{cmd:. tabulate school}{p_end}
{phang2}{cmd:. summarize income, detail}{p_end}

{pstd}Test analysis code on several simulated samples, one per seed:{p_end}
{phang2}{cmd:. forvalues s = 1/5 {c -(}}{p_end}
{phang2}{cmd:.     ctosim using "household.xlsx", n(300) seed(`s') config("realistic.json") saving("sim/seed`s'") replace clear}{p_end}
{phang2}{cmd:.     do "analysis.do"}{p_end}
{phang2}{cmd:. {c )-}}{p_end}

{pstd}Check that the answers respect the form's constraint, here
{it:(. >= 0 and . <= 10) or . = 99 or . = 88}:{p_end}
{phang2}{cmd:. ctosim using "household.xlsx", n(500) clear}{p_end}
{phang2}{cmd:. assert inrange(people_never_respect, 0, 10) | inlist(people_never_respect, 88, 99) if !missing(people_never_respect)}{p_end}


{title:Stored results}

{pstd}
{cmd:ctosim} stores the following in {cmd:r()}:

{synoptset 16 tabbed}{...}
{p2col 5 16 20 2: Scalars}{p_end}
{synopt:{cmd:r(N)}}number of submissions simulated{p_end}
{synopt:{cmd:r(warnings)}}number of warnings in the run report{p_end}
{synopt:{cmd:r(unsatisfied)}}number of questions whose constraint no answer could satisfy (0 when none){p_end}
{synopt:{cmd:r(formerrors)}}number of fields with form errors, which would block an enumerator (0 when none){p_end}
{synopt:{cmd:r(problems)}}with injected problems: number of submissions that carry one{p_end}

{p2col 5 16 20 2: Macros}{p_end}
{synopt:{cmd:r(seed)}}seed used{p_end}
{synopt:{cmd:r(form_id)}}form id{p_end}
{synopt:{cmd:r(outdir)}}output directory (absolute path){p_end}
{synopt:{cmd:r(wide)}}wide CSV file (absolute path){p_end}
{synopt:{cmd:r(report)}}{cmd:run-report.txt} (absolute path){p_end}
{synopt:{cmd:r(fakepreload)}}with {opt fakepreload}: the fictitious files, separated by spaces (empty when none was needed){p_end}
{synopt:{cmd:r(answerkey)}}with injected problems: {cmd:answer-key.csv} (absolute path){p_end}


{pstd}
{bf:Unsatisfied constraints.} When no answer met a question's constraint,
{cmd:ctosim} leaves the question blank and lists it, in red, under
{bf:Unsatisfied constraints} in the Results window and in the run report: the
question's name, its row in the XLSForm, its constraint, the submissions
concerned and the values of the fields the constraint refers to.
This usually means a constraint that cannot hold given the answers it refers
to (for example {it:. >= ${age} and . <= 60} after an age of 90 was
accepted): check the form. {cmd:r(unsatisfied)} gives the number of such
questions.{p_end}

{pstd}
{bf:Form errors.} Some expressions of a form cannot be evaluated in some
situations, for example a constraint comparing an answer with a field of a
repeat from outside that repeat ({it:. != ${phone}}, where {it:phone} is
repeated): SurveyCTO Collect would stop with an error there and the
enumerator would be blocked. {cmd:ctosim} leaves the question blank and goes
on, so that one run shows every such problem; when the walk cannot get past
a point, the submission is cut short there. The fields concerned are listed,
in red, under {bf:FORM ERRORS} in the Results window and at the top of the
run report: the field's name, its row in the XLSForm, the engine's message
and the submissions concerned. {cmd:r(formerrors)} gives the number of such
fields.{p_end}

{pstd}
{bf:Final message.} When the form has form errors or unsatisfied
constraints, {cmd:ctosim} ends with a message in red, shown last, after the
data are loaded (and after the do-file of {opt dofile()} has run, or before
its error when it fails): it gives the number of form errors (that would
block an enumerator) and of unsatisfied constraints, and the absolute path of
the run report {cmd:run-report.txt}. Fix the form before using it in the
field.{p_end}


{marker fieldwork}{...}
{title:Fieldwork plan}

{pstd}
{cmd:ctosim} simulates the fieldwork as it happens, so that data-quality
checks such as {cmd:ipacheck} find only the problems you put in the data.
Each enumerator works days of interviews one after the other:{p_end}

{phang2}- an enumerator's interviews never overlap, and at least ten minutes
separate two of them (travel and rest);{p_end}
{phang2}- every interview starts within working hours: from the time of day of
the configuration's {cmd:start} for nine hours (an interview may end later);{p_end}
{phang2}- an enumerator works in one village a day: its interviews that day are
at most 3 km apart, and the GPS answers of an interview are within 30 m of the
household (inside the configuration's {cmd:area}, when it has one);{p_end}
{phang2}- an enumerator does at most {cmd:interviewsPerDay} interviews a day (4 by
default, set in the configuration), fewer when the form is long.{p_end}

{pstd}
With {opt id(name)}, or the configuration's {cmd:id}, each submission gets
its own value of that question, so that survey ids are distinct: among the
values its configuration allows, its choices (also those loaded by
{cmd:search()}, with {opt fakepreload} for instance) or its integer range.
When there are fewer possible values than submissions, some ids are used
twice and a warning says so. {it:name} is a single word.{p_end}

{pstd}
The length of fieldwork follows from the number of interviews, of
enumerators and the interviews' duration. When the interviews do not fit in
{cmd:fieldworkDays}, the fieldwork lasts longer and a warning says so.{p_end}


{marker problems}{...}
{title:Injected problems}

{pstd}
To test or teach data-quality checks ({cmd:ipacheck} and your own do-files),
{cmd:ctosim} can put problems into the fieldwork {it:on purpose}, and list
each one in an answer key, so that you can compare what your checks found with
what was put there. With option {opt problems}, or a {cmd:problems} object in
the configuration, these problems are injected; every one of them could happen
with SurveyCTO Collect:{p_end}

{phang2}- {bf:resent}: the same submission sent twice (a copy with another {cmd:KEY}), found by {cmd:ipacheckids} (or {cmd:ipacheckdups} without a survey id);{p_end}
{phang2}- {bf:reinterview}: a household interviewed again, with the same survey id and other answers ({cmd:ipacheckids});{p_end}
{phang2}- {bf:short} and {bf:long}: an interview far too short (15-30% of its normal duration) or too long (3 to 6 times), for {cmd:ipacheckoutliers} on the duration;{p_end}
{phang2}- {bf:offhours}: an interview started in the evening, outside working hours ({cmd:ipachecktimeuse});{p_end}
{phang2}- {bf:overlap}: an enumerator's interview of another household started before the end of its previous one, sharing at least five minutes;{p_end}
{phang2}- {bf:travel}: an impossible trip, an interview too far from the enumerator's previous one for the time between them (only when the form has a GPS question);{p_end}
{phang2}- {bf:outlier}: one numeric answer far from the values usually drawn for its question (beyond the fences {cmd:ipacheckoutliers} uses), yet still accepted by the form's constraint at that moment, as Collect would allow, also when the constraint depends on other answers (a second income at most the first, for instance); when a question has no room for such a value, another question gets it, or a later submission.{p_end}

{pstd}
Some enumerators are also {bf:faulty}, on all their interviews, so that
checks comparing enumerators ({cmd:ipacheckenumdb}) can find them:{p_end}

{phang2}- {bf:tooFast}: the enumerator goes too fast, spending much less time on each question, with more speed violations;{p_end}
{phang2}- {bf:skipModules}: the enumerator answers No to filter questions to skip the modules they open, and declares fewer repeat instances (a smaller household roster) than the respondent would, also in rosters built by an "add another member?" question, where it answers No earlier;{p_end}
{phang2}- {bf:dontKnow}: the enumerator answers "don't know" or "refused" codes (the configuration's {cmd:specialValues}) where the form accepts them;{p_end}
{phang2}- {bf:fakeRefusal}: the enumerator declares refusals to avoid interviews: on average about twice the refusals of the other enumerators (about 80% consent when the others get 90%), which the consent rate of {cmd:ipacheckenumdb} shows. It needs the consent questions (below).{p_end}

{pstd}
Each fault gets one enumerator of its own when there are enough: at least
half of the enumerators always stay clean, so that the faulty ones stand
out. With fewer than eight enumerators the faults are cumulated on the same
enumerators, and a warning says so; use more enumerators (twice the number
of faults) for one enumerator per fault, and enough submissions (at least
ten per enumerator) for the checks to tell them apart.{p_end}

{pstd}
{bf:Consent.} Consent is simulated per interview only when the consent
questions are named in the configuration: its {cmd:consent} key lists them
in {cmd:fields}, in the order the form asks them ({cmd:ctosim} never
guesses them from their names). A respondent then consents with the consent
rate, {cmd:rate}, 0.9 (90%) by default, and answers yes to every consent
question; a respondent who refuses answers no to the last one. {cmd:yes} and
{cmd:no} give the choice values, 1 and 0 by default. Without the key,
consent questions are answered like any other question.{p_end}

{pstd}
A duplicate is two submissions with the same survey id, whatever their times;
an overlap is two interviews of one enumerator with different ids whose times
overlap. The configuration's {cmd:problems} object sets the share of
submissions carrying each problem: {cmd:resent} and {cmd:reinterview} (0.01 by
default), {cmd:shortDuration}, {cmd:longDuration}, {cmd:offHours},
{cmd:overlap}, {cmd:travel} and {cmd:outliers} (0.02); its {cmd:faults} key
is the list of faults to give enumerators, all four by default
({cmd:"faults": ["dontKnow"]} for one, {cmd:[]} for none);
{cmd:{c -(}{c )-}} asks for every problem at its default rate, {cmd:{c -(}"travel": 0{c )-}} for every problem
but travel. Option {opt problems} uses the configuration's rates when it has
them, the defaults otherwise.{p_end}

{pstd}
The data in memory are loaded as usual: the answer key is a separate file,
{cmd:answer-key.csv} in the output directory, never merged into them.
{cmd:r(answerkey)} returns its path and {cmd:r(problems)} the number of
submissions with a problem. It has one row per problem and field concerned,
with the columns:{p_end}

{synoptset 16}{...}
{synopt:{cmd:KEY}} the submission that carries the problem{p_end}
{synopt:{cmd:enumerator}} its username{p_end}
{synopt:{cmd:family}} {cmd:duplicate}, {cmd:duration}, {cmd:schedule}, {cmd:travel}, {cmd:outlier} or {cmd:enumerator} (faults){p_end}
{synopt:{cmd:problem}} one of the problems above{p_end}
{synopt:{cmd:check}} the check expected to find it ({cmd:custom} for overlaps and trips){p_end}
{synopt:{cmd:field}} the field concerned (survey id, duration, start time, GPS){p_end}
{synopt:{cmd:value}} its value in the submission{p_end}
{synopt:{cmd:clean_value}} its value without the problem (the answer an ordinary enumerator would give, or the normal duration){p_end}
{synopt:{cmd:related_KEY}} the other submission involved: the original of a duplicate, the previous interview of an overlap or a trip{p_end}
{synopt:{cmd:kind}} {cmd:deterministic} (an exact check finds it every time) or {cmd:statistical} (an outlier test may miss it, or flag other submissions){p_end}

{phang2}{cmd:. ctosim using "household.xlsx", n(200) id(hhid) problems replace clear}{p_end}
{phang2}{cmd:. preserve}{p_end}
{phang2}{cmd:. import delimited using "`r(outdir)'/answer-key.csv", varnames(1) stringcols(_all) clear}{p_end}
{phang2}{cmd:. bysort key (problem): keep if _n == 1}{p_end}
{phang2}{cmd:. tempfile answers}{p_end}
{phang2}{cmd:. save `answers'}{p_end}
{phang2}{cmd:. restore}{p_end}
{phang2}{cmd:. merge 1:1 key using `answers', keepusing(problem check) nogenerate}{p_end}


{marker fakepreload}{...}
{title:Fictitious preload}

{pstd}
Most forms read preloaded data from attached CSV files ({cmd:pulldata()},
{cmd:search()}). When the real files are not available (they often hold
personal data), every preloaded value is blank, repeats counted from them are
empty and the parts of the form that test them are never simulated. With
{opt fakepreload}, {cmd:ctosim} invents the files that {opt media()} lacks
from the form's own use of them: the files and columns it reads, the keys it
looks up, the choice lists it loads with {cmd:search()} and their filters.
The questions used as keys (a household identifier typed by the enumerator,
for instance) are answered with keys of the invented files, so that the
lookups succeed. The values are invented and only follow the form's use of
the files; they are meant for testing a form or analysis code when the real
preload is not available, never for analysis. Real files present in
{opt media()} are used as they are.

{pstd}
The files are written under {cmd:attachments/} in the output directory, where
they can be read or edited, then reused with {opt media()}. The run report and
the Results window list them on a {bf:Fictitious attachments} line, and
{cmd:r(fakepreload)} returns their names.{p_end}

{phang2}{cmd:. ctosim using "household.xlsx", n(100) fakepreload dofile("import_household.do") replace clear}{p_end}
{phang2}{cmd:. display "`r(fakepreload)'"}{p_end}


{title:Reporting a problem}

{pstd}
Send an e-mail to {browse "mailto:mzenaki@alum.mit.edu?subject=[ctosim] ":mzenaki@alum.mit.edu}
with a subject that starts with {bf:[ctosim]}, for example
{it:[ctosim] error 601 with an XLSForm}. Please include:

{phang2}1. the {cmd:ctosim} command you typed;{p_end}
{phang2}2. the Stata version (type {cmd:about});{p_end}
{phang2}3. the version of ctosim (0.1.0; see the first line of this help file);{p_end}
{phang2}4. the messages {cmd:ctosim} displayed, and {cmd:run-report.txt} from the output directory when there is one;{p_end}
{phang2}5. if possible, the form (or a small form that shows the problem).{p_end}
