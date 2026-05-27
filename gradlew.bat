@ECHO OFF
SET DIR=%~dp0
"%JAVA_HOME%\bin\java.exe" -classpath "%DIR%\gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
IF %ERRORLEVEL% NEQ 0 java -classpath "%DIR%\gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
