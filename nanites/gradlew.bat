@echo off
if not defined JAVA_HOME (echo Set JAVA_HOME to JDK17 for Forge or JDK21 for Fabric & exit /b 1)
"%JAVA_HOME%\bin\java.exe" -classpath "%~dp0gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
