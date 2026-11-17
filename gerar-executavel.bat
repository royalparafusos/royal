@echo off
title Gerando Royal Pontos.exe
cd /d "%~dp0"
where mvn >/dev/null 2>/dev/null || (echo Maven nao encontrado. Veja o README, passo 1. & pause & exit /b 1)
echo [1/2] Compilando...
call mvn -q clean package -DskipTests || (pause & exit /b 1)
echo [2/2] Criando o executavel...
if exist dist rmdir /s /q dist
mkdir target\app
copy /y target\royal-pontos.jar target\app\ >nul
jpackage --type app-image --input target\app --main-jar royal-pontos.jar --main-class br.com.royal.pontos.Launcher --name "Royal Pontos" --dest dist --java-options "-Dfile.encoding=UTF-8" || (pause & exit /b 1)
echo.
echo Pronto! Abra: dist\Royal Pontos\Royal Pontos.exe
pause
