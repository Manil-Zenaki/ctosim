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
The optional JSON configuration sets the seed, the number of submissions, the
fieldwork period and time zone, the number of enumerators, the rates of
missing answers, special codes and speed violations, repeat sizes, default
numeric ranges, and per-field overrides (a fixed value, a range, choice
weights, a list of texts). See the project's developer guide.


{title:Examples}

{pstd}Simulate 300 submissions and import them as plain data:{p_end}
{phang2}{cmd:. ctosim using "household.xlsx", n(300) seed(42) clear}{p_end}

{pstd}Load them with the form's SurveyCTO import do-file:{p_end}
{phang2}{cmd:. ctosim using "household.xlsx", n(300) seed(42) config("sim.json") dofile("import_household.do") replace clear}{p_end}


{title:Stored results}

{pstd}
{cmd:ctosim} stores the following in {cmd:r()}:

{synoptset 16 tabbed}{...}
{p2col 5 16 20 2: Scalars}{p_end}
{synopt:{cmd:r(N)}}number of submissions simulated{p_end}
{synopt:{cmd:r(warnings)}}number of warnings in the run report{p_end}

{p2col 5 16 20 2: Macros}{p_end}
{synopt:{cmd:r(seed)}}seed used{p_end}
{synopt:{cmd:r(form_id)}}form id{p_end}
{synopt:{cmd:r(outdir)}}output directory (absolute path){p_end}
{synopt:{cmd:r(wide)}}wide CSV file (absolute path){p_end}
{synopt:{cmd:r(report)}}{cmd:run-report.txt} (absolute path){p_end}
