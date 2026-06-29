@echo off
setlocal
if not defined JAVA_HOME (
  for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-11*") do set "JAVA_HOME=%%~D"
)
if not defined JAVA_HOME (
  echo ERROR: Java 11 JDK not found. Install Eclipse Temurin 11 or set JAVA_HOME.
  exit /b 1
)
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "SIDELOAD=%USERPROFILE%\.runelite\sideloaded-plugins"
set "NEW_JAR=%SIDELOAD%\osrs-journal-plugin-1.0.0.jar"

cd /d "%~dp0"
call gradlew.bat jar %*
if %ERRORLEVEL% neq 0 exit /b %ERRORLEVEL%

if not exist "%SIDELOAD%" mkdir "%SIDELOAD%"

copy /Y "build\libs\osrs-journal-plugin-1.0.0.jar" "%SIDELOAD%\" >nul
if %ERRORLEVEL% neq 0 (
  echo.
  echo ERROR: Could not copy the plugin jar. Close RuneLite and run build.bat again.
  exit /b 1
)

echo.
echo Installed: %NEW_JAR%
echo.
echo Restart RuneLite if it was open during the build so the new plugin loads.
