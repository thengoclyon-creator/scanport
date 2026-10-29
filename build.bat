@echo off
chcp 65001 > nul
echo ========================================================
echo       BIEN DICH UNG DUNG WI-FI PORT SCANNER (JAVA)
echo ========================================================

if not exist bin (
    mkdir bin
)

echo [*] Dang bien dich ma nguon Java...
javac -d bin -encoding UTF-8 src\com\wifiscan\model\*.java src\com\wifiscan\service\*.java src\com\wifiscan\ui\components\*.java src\com\wifiscan\ui\*.java src\com\wifiscan\*.java

if %ERRORLEVEL% EQU 0 (
    echo [OK] Bien dich thanh cong vao thu muc bin/!
    echo Ban co the chay file run.bat de mo giao dien do hoa.
) else (
    echo [LOI] Co loi xay ra trong qua trinh bien dich.
)

if "%1" neq "/nopause" pause
