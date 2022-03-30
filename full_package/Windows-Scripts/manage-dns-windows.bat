@ECHO OFF
SETLOCAL EnableDelayedExpansion

title Manage pDNSf DNS
color 0A
chcp 65001 > nul
mode con: cols=90 lines=27

:: BatchGotAdmin
REM  --> Check for permissions
>nul 2>&1 "%SYSTEMROOT%\system32\cacls.exe" "%SYSTEMROOT%\system32\config\system"

REM --> If error flag set, we do not have admin.
if '%errorlevel%' NEQ '0' (
    color 0B
    echo Requesting administrative privileges...
    echo.
    echo                   ▓▓▓▓▓▓▓▓                   
    echo          ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓          
    echo      ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░░░░░░░░▓▓▓▓▓▓      
    echo      ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░░░░░░░░░░░▓▓▓      
    echo      ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░░░░░░░░░░░▓▓▓      
    echo      ▓▓▓▓▓▓▓▓▓▓▓░░░░░░▓▓▓▓▓▓▓░░░░░░░▓▓▓      
    echo      ▓▓▓▓▓▓▓▓▓░░░▓▓░░▓░▓▓▓░░▓▓▓░░░░░▓▓▓      
    echo      ▓▓▓▓▓▓▓░░░░░░░░░░▓▓▓▓▓▓▓▓▓▓░░░░▓▓▓      
    echo      ▓▓▓▓▓▓░░░▓▓▓░░░▓▓░░░▓▓░░░▓▓▓░░░▓▓▓      
    echo      ▓▓▓▓▓▓░░░▓▓▓░░░▓▓░░░▓▓░░░▓▓▓░░░▓▓▓      
    echo      ▓▓▓▓▓▓▓░░░░░░░░░░▓▓▓▓▓▓▓▓▓▓░░░░▓▓▓      
    echo      ▓▓▓▓▓▓▓▓░░░▓▓░░▓▓░░▓▓░░▓▓▓░░░░▓▓▓▓      
    echo       ▓▓▓▓▓▓▓▓▓░░░░░░░▓▓▓▓▓▓▓░░░░░▓▓▓▓       
    echo        ▓▓▓▓▓▓▓▓▓▓▓░░░░▓▓▓▓░░░░░░░▓▓▓▓        
    echo         ▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░░░░░░░▓▓▓▓         
    echo           ▓▓▓▓▓▓▓▓▓▓▓▓░░░░░░░░▓▓▓▓           
    echo            ▓▓▓▓▓▓▓▓▓▓▓░░░░░░▓▓▓▓▓            
    echo              ▓▓▓▓▓▓▓▓▓░░░▓▓▓▓▓▓              
    echo                 ▓▓▓▓▓▓▓▓▓▓▓▓                 
    echo                     ▓▓▓▓                     
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
        set w=[107m░[40m
    ) else (
        set red=
        set green=
        set yellow=
        set magenta=
        set cyan=
        set w= 
    )
)

echo.
echo.

:menu
set "answer="
set "answer2="
set "ans1="
set "ans2="
set "COUNTER="
set "AGREE="

echo %cyan%                                                                       ▓▓▓▓▓▓▓▓▓▓        
echo %yellow%Select your Option:%cyan%                                            ▓▓▓▓▓▓▓▓▓▓▓▓▓%w%%w%%w%%w%%cyan%▓▓▓▓▓▓▓▓ 
echo                                                                ▓▓▓▓▓▓▓▓▓▓▓▓▓%w%%w%%w%%w%%w%%w%%w%%w%%w%%w%%cyan%▓▓ 
echo %yellow% ------------------------------------------------------------%cyan% ▓▓▓▓▓▓▓▓▓▓▓▓▓▓%w%%w%%w%%w%%w%%w%%w%%w%%w%%w%%cyan%▓▓▓
echo   [1] Set pDNSf DNS for Current Active Network Adapter(s)     ▓▓▓▓▓▓▓▓%w%%w%%w%%w%%w%%w%%cyan%▓▓▓▓▓%w%%w%%w%%w%%w%%w%%cyan%▓▓
echo %green%  [2] Set pDNSf DNS for All Network Adapters%cyan%                  ▓▓▓▓▓▓%w%%w%%w%%cyan%▓▓%w%%w%%cyan%▓%w%%cyan%▓%w%%w%%cyan%▓▓▓%w%%w%%w%%w%%cyan%▓▓
echo %green%  [3] Set pDNSf DNS for Desired Network Adapter%cyan%               ▓▓▓▓▓%w%%w%%w%%w%%w%%w%%w%%w%%w%%cyan%▓▓▓▓▓▓▓▓%w%%w%%w%%cyan%▓▓
echo %yellow% ------------------------------------------------------------%cyan% ▓▓▓▓▓%w%%w%%cyan%▓▓▓%w%%w%%cyan%▓▓%w%%w%%cyan%▓%w%%w%%w%%cyan%▓▓%w%%w%%cyan%▓▓▓
echo   [4] Remove pDNSf DNS from Current Active Network Adapter%red% **%cyan%  ▓▓▓▓▓%w%%w%%w%%w%%w%%w%%w%%w%%cyan%▓▓▓▓▓▓▓%w%%w%%w%%cyan%▓▓ 
echo %green%  [5] Remove pDNSf DNS from All Network Adapters %red%**%cyan%            ▓▓▓▓▓▓%w%%w%%cyan%▓▓%w%%w%%cyan%▓%w%%cyan%▓%w%%w%%cyan%▓▓%w%%w%%w%%w%%cyan%▓▓ 
echo %green%  [6] Remove pDNSf DNS from Desired Network Adapter %red%**%cyan%          ▓▓▓▓▓▓▓%w%%w%%w%%w%%w%%cyan%▓▓▓▓%w%%w%%w%%w%%w%%cyan%▓▓  
echo %yellow% ------------------------------------------------------------%cyan%    ▓▓▓▓▓▓▓▓▓▓▓%w%%w%%w%%w%%w%%w%%w%%cyan%▓▓▓   
echo %green%  [0] Exit%cyan%                                                        ▓▓▓▓▓▓▓▓▓▓%w%%w%%w%%w%%w%%w%%cyan%▓▓▓    
echo                                                                     ▓▓▓▓▓▓▓▓%w%%w%%w%%w%%cyan%▓▓▓      
echo                                                                       ▓▓▓▓▓▓▓▓▓▓▓        
echo    %red%**%magenta% Sets (Default) DHCP DNS after removing DNS.%cyan%                         ▓▓▓            
echo   %red% WARNING: There will be No Internet Access if pDNSf is NOT running^^!^^!
echo.%yellow%
set /p answer=Enter selection:
if /i "!answer!" EQU "1" goto opt1
if /i "!answer!" EQU "2" goto opt2
if /i "!answer!" EQU "3" goto opt3
if /i "!answer!" EQU "4" goto opt4
if /i "!answer!" EQU "5" goto opt5
if /i "!answer!" EQU "6" goto opt6
if /i "!answer!" EQU "0" exit
cls
echo.
echo %magenta%  The input is invalid^^!
goto menu

:opt1
cls
echo.
echo Preparing to set pDNSf DNS to the following Network Adapters:
for /f "usebackq skip=3 tokens=2,3*" %%f in (`netsh interface show interface`) do (
    if /i "%%f"=="Connected" (
        echo %green%%%h%yellow%
    )
)
set /p ans1="Are you sure?(y/n):"
if /i "!ans1!"=="y" goto opt1a
if /i "!ans1!"=="Y" goto opt1a
cls
echo.
echo %cyan% Nothing has changed^^!
goto menu
:opt1a
for /f "usebackq skip=3 tokens=2,3*" %%f in (`netsh interface show interface`) do (
    if /i "%%f"=="Connected" (
        echo Setting pDNSf DNS for "%%h"...
        netsh interface ipv4 set dnsservers name="%%h" source=static address=127.0.0.1 register=primary validate=no
        netsh interface ipv6 set dnsservers name="%%h" source=static address=::1 register=primary validate=no
    )
)
goto done

:opt2
cls
echo.
echo Preparing to set pDNSf DNS to the following Network Adapters:
for /f "usebackq skip=3 tokens=2,3*" %%f in (`netsh interface show interface`) do (
        echo %green%%%h%yellow%
)
set /p ans1="Are you sure?(y/n):"
if /i "!ans1!"=="y" goto opt2a
if /i "!ans1!"=="Y" goto opt2a
cls
echo.
echo %cyan% Nothing has changed^^!
goto menu
:opt2a
for /f "usebackq skip=3 tokens=2,3*" %%f in (`netsh interface show interface`) do (
    echo Setting pDNSf DNS for "%%h"...
    netsh interface ipv4 set dnsservers name="%%h" source=static address=127.0.0.1 register=primary validate=no
    netsh interface ipv6 set dnsservers name="%%h" source=static address=::1 register=primary validate=no
)
goto done

:opt3
cls
echo.
:opt3a
echo Choose the Network Adapter to set pDNSf DNS:
echo.
set /A COUNTER=0
for /f "usebackq skip=3 tokens=2,3*" %%f in (`netsh interface show interface`) do (
    set /a COUNTER=COUNTER+1
    echo [%green%!COUNTER!%yellow%] %cyan%{%%f}%yellow%  	%green%%%h%yellow%
)
echo.
echo [%green%0%yellow%] %green%Back%yellow%
set /p answer2="Enter selection:"
call :isInt !answer2! || cls && (echo %magenta% The input is invalid^^!%yellow%) && goto opt3a
if /i "!answer2!"=="0" (
    cls
    echo.
    echo.
    goto menu
)
if !answer2! GTR 0 if !answer2! LEQ !COUNTER! (
    set /A COUNTER=0
    set /A AGREE=0
    set /p ans2="Are you sure?(y/n):"
    if /i "!ans2!"=="y" set /A AGREE=1
    if /i "!ans2!"=="Y" set /A AGREE=1
    if !AGREE! EQU 0 (
        cls
        echo.
        echo %cyan% Nothing has changed^^!
        goto menu
    )
    for /f "usebackq skip=3 tokens=2,3*" %%f in (`netsh interface show interface`) do (
        set /a COUNTER=COUNTER+1
        if !answer2! EQU !COUNTER! (
            echo.
            echo Setting pDNSf DNS for "%%h"...
            netsh interface ipv4 set dnsservers name="%%h" source=static address=127.0.0.1 register=primary validate=no
            netsh interface ipv6 set dnsservers name="%%h" source=static address=::1 register=primary validate=no
        )
    )
    goto done
)
cls && (echo %magenta% The input is invalid^^!%yellow%) && goto opt3a


:opt4
cls
echo.
echo Preparing to reset DNS of the following Network Adapters to default:
for /f "usebackq skip=3 tokens=2,3*" %%f in (`netsh interface show interface`) do (
    if /i "%%f"=="Connected" (
        echo %green%%%h%yellow%
    )
)
set /p ans1="Are you sure?(y/n):"
if /i "!ans1!"=="y" goto opt4a
if /i "!ans1!"=="Y" goto opt4a
cls
echo.
echo %cyan% Nothing has changed^^!
goto menu
:opt4a
for /f "usebackq skip=3 tokens=2,3*" %%f in (`netsh interface show interface`) do (
    if /i "%%f"=="Connected" (
        echo Reverting to DHCP DNS for "%%h"...
        netsh interface ipv4 set dnsservers name="%%h" source=dhcp
        netsh interface ipv6 set dnsservers name="%%h" source=dhcp
    )
)
goto done

:opt5
cls
echo.
echo Preparing to reset DNS of the following Network Adapters to default:
for /f "usebackq skip=3 tokens=2,3*" %%f in (`netsh interface show interface`) do (
        echo %green%%%h%yellow%
)
set /p ans1="Are you sure?(y/n):"
if /i "!ans1!"=="y" goto opt5a
if /i "!ans1!"=="Y" goto opt5a
cls
echo.
echo %cyan% Nothing has changed^^!
goto menu
:opt5a
for /f "usebackq skip=3 tokens=2,3*" %%f in (`netsh interface show interface`) do (
    echo Reverting to DHCP DNS for "%%h"...
    netsh interface ipv4 set dnsservers name="%%h" source=dhcp
    netsh interface ipv6 set dnsservers name="%%h" source=dhcp
)
goto done

:opt6
cls
echo.
:opt6a
echo Choose the Network Adapter to Reset it to Default DHCP DNS:
echo.
set /A COUNTER=0
for /f "usebackq skip=3 tokens=2,3*" %%f in (`netsh interface show interface`) do (
    set /a COUNTER=COUNTER+1
    echo [%green%!COUNTER!%yellow%] %cyan%{%%f}%yellow%  	%green%%%h%yellow%
)
echo.
echo [%green%0%yellow%] %green%Back%yellow%
set /p answer2="Enter selection:"
call :isInt !answer2! || cls && (echo %magenta% The input is invalid^^!%yellow%) && goto opt6a
if /i "!answer2!"=="0" (
    cls
    echo.
    echo.
    goto menu
)
if !answer2! GTR 0 if !answer2! LEQ !COUNTER! (
    set /A COUNTER=0
    set /A AGREE=0
    set /p ans2="Are you sure?(y/n):"
    if /i "!ans2!"=="y" set /A AGREE=1
    if /i "!ans2!"=="Y" set /A AGREE=1
    if !AGREE! EQU 0 (
        cls
        echo.
        echo %cyan% Nothing has changed^^!
        goto menu
    )
    for /f "usebackq skip=3 tokens=2,3*" %%f in (`netsh interface show interface`) do (
        set /a COUNTER=COUNTER+1
        if !answer2! EQU !COUNTER! (
            echo.
            echo Reverting to DHCP DNS for "%%h"...
            netsh interface ipv4 set dnsservers name="%%h" source=dhcp
            netsh interface ipv6 set dnsservers name="%%h" source=dhcp
        )
    )
    goto done
)
cls && (echo %magenta% The input is invalid^^!%yellow%) && goto opt6a


:isInt <str>
for /f "delims=0123456789" %%a in ("%1") do exit /b 1
exit /b 0

:done
echo.
echo Done.
echo.
echo Flushing DNS cache...
ipconfig /flushdns > nul
echo Re-registering DNS name...
ipconfig /registerdns > nul
echo.
echo %green% ..:# Finished #:..
echo.%green%Press any key to get back to Menu...
pause >nul
cls
echo.
echo.
goto menu