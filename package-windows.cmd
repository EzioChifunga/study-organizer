@echo off
REM ===========================================================================
REM  Gera o Study Organizer como aplicativo Windows independente.
REM
REM  O resultado sai em:  target\dist\Study Organizer\Study Organizer.exe
REM
REM  Essa pasta e autossuficiente: leva um Java embutido dentro dela, entao
REM  roda em qualquer PC com Windows 64 bits mesmo sem Java instalado. Basta
REM  copiar a pasta inteira - nao existe instalacao.
REM
REM  Para gerar, este PC precisa de um JDK 17 ou mais novo (o jpackage vem
REM  junto). Os PCs que so vao RODAR o app nao precisam de nada.
REM ===========================================================================

setlocal

set APP_NAME=Study Organizer
set APP_VERSION=1.0.0
set MAIN_JAR=study-organizer-1.0.0.jar
set MAIN_CLASS=com.study.organizer.Launcher

cd /d "%~dp0"

echo.
echo [1/3] Compilando e rodando os testes...
REM O caminho completo e obrigatorio: o cmd.exe nao procura executaveis na
REM pasta atual, entao um "mvnw.cmd" solto daria "nao reconhecido".
call "%~dp0mvnw.cmd" -B clean package
if errorlevel 1 (
    echo.
    echo FALHOU na compilacao. Nada foi empacotado.
    exit /b 1
)

echo.
echo [2/3] Limpando a saida anterior...
if exist "target\dist" rmdir /s /q "target\dist"
mkdir "target\dist"

echo.
echo [3/3] Empacotando com jpackage (isso leva um minuto)...
jpackage ^
  --type app-image ^
  --name "%APP_NAME%" ^
  --app-version "%APP_VERSION%" ^
  --vendor "Study Organizer" ^
  --description "Acompanhe suas sessoes de estudo em um cofre do Obsidian" ^
  --input "target\app" ^
  --main-jar "%MAIN_JAR%" ^
  --main-class "%MAIN_CLASS%" ^
  --dest "target\dist" ^
  --java-options "-Xmx512m"

if errorlevel 1 (
    echo.
    echo FALHOU no jpackage.
    echo Confira se o 'jpackage' esta no PATH:  jpackage --version
    exit /b 1
)

echo.
echo ===========================================================================
echo  PRONTO
echo.
echo  O aplicativo esta em:
echo     %cd%\target\dist\%APP_NAME%\
echo.
echo  Para usar aqui: abra "%APP_NAME%.exe" dentro dessa pasta.
echo  Para levar a outro PC: copie a pasta "%APP_NAME%" inteira.
echo.
echo  Suas notas ficam numa pasta "vault" ao lado do .exe, entao a pasta
echo  toda pode viver num pen drive e ir junto com voce.
echo ===========================================================================
echo.

endlocal
