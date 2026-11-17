@echo off
title Instalando Royal Pontos
echo Instalando o Royal Pontos. Isso pode levar alguns minutos...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0instalar.ps1"
echo.
pause
