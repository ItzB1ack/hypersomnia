@echo off
chcp 65001 >nul
set PYTHONIOENCODING=utf-8
cd /d "%~dp0"
python run_tool.py
:loop
echo.
set "NAME="
set /p NAME=Script name (all = everything, empty = exit):
if "%NAME%"=="" exit /b
python run_tool.py %NAME%
goto loop