@echo off
chcp 65001 > nul
echo ========================================================
echo       KHOI DONG UNG DUNG WI-FI PORT SCANNER (CLI)
echo ========================================================

if not exist bin\com\wifiscan\Main.class (
    echo [*] Dang bien dich...
    call build.bat
)

java -Dfile.encoding=UTF-8 -cp bin com.wifiscan.Main --cli %*
if "%1" neq "/nopause" pause
