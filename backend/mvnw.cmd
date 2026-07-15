@REM Maven Wrapper startup script for Windows

@echo off
setlocal

set WRAPPER_JAR="%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar"
set WRAPPER_URL="https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar"

if exist %WRAPPER_JAR% (
    if not "%MAVEN_TERMINATE_CMD%"=="on" goto endInit
    cmd /c exit /B %ERRORLEVEL%
) else (
    powershell -Command "& {" ^
        "$webclient = new-object System.Net.WebClient; " ^
        "if (-not ([Environment]::Version.Major -gt 10 -or [Environment]::Version.Minor -gt 10)) { " ^
        "   $webclient.credentials = [System.Net.CredentialCache]::DefaultCredentials; " ^
        "}; " ^
        "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; " ^
        "$webclient.DownloadFile('%WRAPPER_URL%', '%WRAPPER_JAR%')" ^
    "}"
)

@REM Provide a "standardized" way to retrieve the CLI args
set MAVEN_CMD_LINE_ARGS=%*

%MAVEN_JAVA_EXE% ^
    "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" ^
    %MAVEN_OPTS% ^
    %MAVEN_DEBUG_OPTS% ^
    -classpath %WRAPPER_JAR% ^
    org.apache.maven.wrapper.MavenWrapperMain ^
    %MAVEN_CMD_LINE_ARGS%

:end
@endlocal & set ERROR_CODE=%ERRORLEVEL%

if not "%MAVEN_TERMINATE_CMD%"=="on" (
    exit /B %ERROR_CODE%
)
