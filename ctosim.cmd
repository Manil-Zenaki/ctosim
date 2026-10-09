@echo off
rem Runs the ctosim command line: ctosim FORM [options]. Build target\ctosim.jar first with mvnw package.
setlocal
set "CTOSIM_JAR=%~dp0target\ctosim.jar"
if not exist "%CTOSIM_JAR%" (
  echo ctosim: %CTOSIM_JAR% not found; build it with "mvnw package" 1>&2
  exit /b 1
)
if defined JAVA_HOME (set "CTOSIM_JAVA=%JAVA_HOME%\bin\java.exe") else (set "CTOSIM_JAVA=java")
"%CTOSIM_JAVA%" -jar "%CTOSIM_JAR%" %*
exit /b %ERRORLEVEL%
