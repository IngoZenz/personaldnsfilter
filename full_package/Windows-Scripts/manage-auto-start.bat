@ECHO OFF
SETLOCAL EnableDelayedExpansion
title Create pDNSf Auto-Start Task
color 0A

:: BatchGotAdmin
REM  --> Check for permissions
>nul 2>&1 "%SYSTEMROOT%\system32\cacls.exe" "%SYSTEMROOT%\system32\config\system"

REM --> If error flag set, we do not have admin.
if '%errorlevel%' NEQ '0' (
    color 0E
    echo Requesting administrative privileges...
    goto UACPrompt
) else ( goto gotAdmin )

:UACPrompt
    SET thisfile="%~f0"
    echo Set UAC = CreateObject^("Shell.Application"^) > "%temp%\getadmin.vbs"
    echo UAC.ShellExecute "cmd.exe", "/c "%thisfile%"", "", "runas", 1 >> "%temp%\getadmin.vbs"

    "%temp%\getadmin.vbs"
    del "%temp%\getadmin.vbs"
    exit /B

:gotAdmin

for /f "usebackq tokens=4 delims=. " %%f in (`ver`) do (
    if "%%f"=="10" (
        set red=[91m
        set green=[92m
        set yellow=[93m
        set magenta=[95m
        set cyan=[96m
    ) else (
        set red=
        set green=
        set yellow=
        set magenta=
        set cyan=
    )
)

cd /d "%~dp0"
pushd "%~dp0"
cd ..
echo.
if not exist "start.bat" (echo %red% Could not find start.bat file, you should copy this bat file to pDNSf folder and run from there.[0m) && goto done

:menu
echo.%yellow%
echo Select your Option:
echo.%green%
echo   [1] Create Auto start task
echo   [2] Create Auto start task (start Minimized)
echo   [3] Create Auto start task (start Hidden)**
echo   [4] Delete Auto start task
echo.%magenta%
echo    ** When started in hidden mode, it can't be stopped in normal way^^!
echo    ** Manageable with remote control client^^!
echo.%yellow%
set /p answer=Enter selection:
if /i "%answer:~,1%" EQU "1" goto opt1
if /i "%answer:~,1%" EQU "2" goto opt2
if /i "%answer:~,1%" EQU "3" goto opt3
if /i "%answer:~,1%" EQU "4" goto opt4
cls
echo.
echo %magenta%  The input is invalid^^!
goto menu

:opt1
powershell -Command "Register-ScheduledTask -TaskName \"pDNSf-Auto-start\" -Force -Action $(New-ScheduledTaskAction -Execute \"cmd.exe\" -Argument '/c cd /d \"%CD%\" && start \"personalDNSfilter\" start.bat') -Settings $(New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries) -Trigger $(New-ScheduledTaskTrigger -AtLogon)"
goto done

:opt2
powershell -Command "Register-ScheduledTask -TaskName \"pDNSf-Auto-start\" -Force -Action $(New-ScheduledTaskAction -Execute \"cmd.exe\" -Argument '/c cd /d \"%CD%\" && start \"personalDNSfilter\" /min start.bat') -Settings $(New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries) -Trigger $(New-ScheduledTaskTrigger -AtLogon)"
goto done

:opt3
powershell -Command "Register-ScheduledTask -TaskName \"pDNSf-Auto-start\" -Force -Action $(New-ScheduledTaskAction -Execute \"cmd.exe\" -Argument '/c cd /d \"%CD%\" && start \"personalDNSfilter\" /min \"javaw.exe\" -classpath ./personalDNSfilter.jar dnsfilter.DNSFilterProxy') -Settings $(New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries) -Trigger $(New-ScheduledTaskTrigger -AtLogon)"
goto done

:opt4
SCHTASKS /Delete /TN "pDNSf-Auto-start" /F
goto done

:done
echo.
echo.%green%Done^^! Press any key to exit...
pause >nul