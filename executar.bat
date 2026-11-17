@echo off
title Royal Pontos
cd /d "%~dp0"
where mvn >/dev/null 2>/dev/null || (echo Maven nao encontrado. Veja o README, passo 1. & pause & exit /b 1)
call mvn -q javafx:run
if errorlevel 1 pause
