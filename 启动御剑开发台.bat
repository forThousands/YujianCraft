@echo off
setlocal
cd /d "%~dp0"
where python >nul 2>nul
if errorlevel 1 (
  echo [Yujian Craft] Python 3 was not found in PATH.
  echo Install Python 3 or add it to PATH, then run this launcher again.
  pause
  exit /b 1
)
python devtools\control_panel\server.py
if errorlevel 1 pause
endlocal
