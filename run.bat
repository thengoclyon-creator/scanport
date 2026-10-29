@echo off
chcp 65001 > nul
echo ========================================================
echo       KHOI DONG UNG DUNG WI-FI PORT SCANNER (GUI)
echo ========================================================

if not exist bin\com\wifiscan\Main.class (
    echo [*] Chua thay file bien dich, dang tien hanh bien dich truoc...
    call build.bat
)

start javaw -Dfile.encoding=UTF-8 -cp bin com.wifiscan.Main
echo [OK] Ung dung da duoc mo tren cua so giao dien!
