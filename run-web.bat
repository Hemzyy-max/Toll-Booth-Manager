@echo off
REM ---------------------------------------------------------------------------
REM  IoT Based Toll Booth Manager - compile and run the REAL TIME WEB application
REM  Usage : double click this file, or type  run-web.bat
REM ---------------------------------------------------------------------------
setlocal
cd /d "%~dp0"
if "%PORT%"=="" set PORT=3000

echo ==========================================
echo  Compiling the toll booth manager project
echo ==========================================
if not exist out mkdir out
javac -d out tollbooth\*.java tollbooth\model\*.java tollbooth\payment\*.java tollbooth\device\*.java tollbooth\exception\*.java tollbooth\service\*.java tollbooth\thread\*.java tollbooth\web\*.java tollbooth\web\exception\*.java
if errorlevel 1 goto compileerror

if exist resources\static (
    if not exist out\static mkdir out\static
    copy /Y resources\static\* out\static\ >nul
)

echo Compilation successful.
echo.
echo Starting the real time web application on port %PORT% ...
echo Open  http://localhost:%PORT%  in a browser.
echo First start creates the accounts  admin / Admin@123  and  operator / Operator@123
echo Press Ctrl+C to stop the server.
echo.
java -cp out tollbooth.web.WebLauncher --port %PORT%
goto end

:compileerror
echo.
echo COMPILATION FAILED.
echo Please check that the JDK (javac) is installed and added to the PATH.
echo.

:end
pause
