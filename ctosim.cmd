@echo off
rem Runs the ctosim command line: ctosim FORM [options]. Build target\ctosim.jar first with mvnw package.
setlocal
set "CTOSIM_JAR=%~dp0target\ctosim.jar"
if not exist "%CTOSIM_JAR%" (
  echo ctosim: %CTOSIM_JAR% not found; build it with "mvnw package" 1>&2
  exit /b 1
)
if defined JAVA_HOME (set "CTOSIM_JAVA=%JAVA_HOME%\bin\java.exe") else (set "CTOSIM_JAVA=java")
rem ctosim writes UTF-8: use the UTF-8 code page during the run, then restore the console's own.
set "CTOSIM_CP="
for /f "tokens=2 delims=:" %%c in ('chcp') do set "CTOSIM_CP=%%c"
if defined CTOSIM_CP set "CTOSIM_CP=%CTOSIM_CP: =%"
if defined CTOSIM_CP set "CTOSIM_CP=%CTOSIM_CP:.=%"
chcp 65001 >nul
"%CTOSIM_JAVA%" -jar "%CTOSIM_JAR%" %*
set "CTOSIM_STATUS=%ERRORLEVEL%"
if defined CTOSIM_CP chcp %CTOSIM_CP% >nul
exit /b %CTOSIM_STATUS%
