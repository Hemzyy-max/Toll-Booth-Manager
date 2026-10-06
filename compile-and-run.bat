@echo off
REM ---------------------------------------------------------------------------
REM  IoT Based Toll Booth Manager System - compile and run script (Windows)
REM  Usage : double click this file, or type  compile-and-run.bat
REM ---------------------------------------------------------------------------
cd /d "%~dp0"

echo ==========================================
echo  Compiling the toll booth manager project
echo ==========================================
if not exist out mkdir out
javac -d out tollbooth\*.java tollbooth\model\*.java tollbooth\payment\*.java tollbooth\device\*.java tollbooth\exception\*.java tollbooth\service\*.java tollbooth\thread\*.java
if errorlevel 1 goto compileerror

echo Compilation successful. Class files are in the "out" folder.
echo.
echo Starting the application (type 10 to exit)...
echo.
java -cp out tollbooth.TollBoothManager
goto end

:compileerror
echo.
echo COMPILATION FAILED.
echo Please check that the JDK (javac) is installed and added to the PATH.
echo.

:end
pause
