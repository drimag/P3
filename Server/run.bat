@echo off
set PATH_TO_FX=%~dp0\javafx-sdk-17.0.14\lib

echo Compiling Server.java...
javac --module-path %PATH_TO_FX% --add-modules javafx.controls,javafx.media Server.java

if %ERRORLEVEL% neq 0 (
    echo Compilation failed.
    pause
    exit /b %ERRORLEVEL%
)

echo Running Server...
java --module-path %PATH_TO_FX% --add-modules javafx.controls,javafx.media Server

pause
