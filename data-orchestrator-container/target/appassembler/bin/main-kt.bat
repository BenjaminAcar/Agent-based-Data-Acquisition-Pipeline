@REM ----------------------------------------------------------------------------
@REM  Copyright 2001-2006 The Apache Software Foundation.
@REM
@REM  Licensed under the Apache License, Version 2.0 (the "License");
@REM  you may not use this file except in compliance with the License.
@REM  You may obtain a copy of the License at
@REM
@REM       http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM  Unless required by applicable law or agreed to in writing, software
@REM  distributed under the License is distributed on an "AS IS" BASIS,
@REM  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@REM  See the License for the specific language governing permissions and
@REM  limitations under the License.
@REM ----------------------------------------------------------------------------
@REM
@REM   Copyright (c) 2001-2006 The Apache Software Foundation.  All rights
@REM   reserved.

@echo off

set ERROR_CODE=0

:init
@REM Decide how to startup depending on the version of windows

@REM -- Win98ME
if NOT "%OS%"=="Windows_NT" goto Win9xArg

@REM set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" @setlocal

@REM -- 4NT shell
if "%eval[2+2]" == "4" goto 4NTArgs

@REM -- Regular WinNT shell
set CMD_LINE_ARGS=%*
goto WinNTGetScriptDir

@REM The 4NT Shell from jp software
:4NTArgs
set CMD_LINE_ARGS=%$
goto WinNTGetScriptDir

:Win9xArg
@REM Slurp the command line arguments.  This loop allows for an unlimited number
@REM of arguments (up to the command line limit, anyway).
set CMD_LINE_ARGS=
:Win9xApp
if %1a==a goto Win9xGetScriptDir
set CMD_LINE_ARGS=%CMD_LINE_ARGS% %1
shift
goto Win9xApp

:Win9xGetScriptDir
set SAVEDIR=%CD%
%0\
cd %0\..\.. 
set BASEDIR=%CD%
cd %SAVEDIR%
set SAVE_DIR=
goto repoSetup

:WinNTGetScriptDir
set BASEDIR=%~dp0\..

:repoSetup
set REPO=


if "%JAVACMD%"=="" set JAVACMD=java

if "%REPO%"=="" set REPO=%BASEDIR%\repo

set CLASSPATH="%BASEDIR%"\etc;"%REPO%"\opaca-container-0.2-SNAPSHOT.jar;"%REPO%"\opaca-model-0.2-SNAPSHOT.jar;"%REPO%"\lombok-1.18.30.jar;"%REPO%"\jackson-databind-2.13.4.1.jar;"%REPO%"\jackson-annotations-2.13.4.jar;"%REPO%"\jackson-core-2.13.4.jar;"%REPO%"\jackson-datatype-jsr310-2.13.1.jar;"%REPO%"\spring-web-5.3.15.jar;"%REPO%"\spring-beans-5.3.15.jar;"%REPO%"\spring-core-5.3.15.jar;"%REPO%"\spring-jcl-5.3.15.jar;"%REPO%"\spring-webmvc-5.3.15.jar;"%REPO%"\spring-aop-5.3.15.jar;"%REPO%"\spring-context-5.3.15.jar;"%REPO%"\spring-expression-5.3.15.jar;"%REPO%"\jiacvi-core-0.2.0-20191128.142909-19.jar;"%REPO%"\kotlin-stdlib-jdk8-1.3.50.jar;"%REPO%"\kotlin-stdlib-jdk7-1.3.50.jar;"%REPO%"\kodein-di-erased-jvm-6.2.1.jar;"%REPO%"\kodein-di-core-jvm-6.2.1.jar;"%REPO%"\kodein-di-conf-jvm-6.2.1.jar;"%REPO%"\jiacvi-common-0.2.0-20191128.142908-19.jar;"%REPO%"\kodein-di-erased-metadata-6.2.1.jar;"%REPO%"\kodein-di-core-metadata-6.2.1.jar;"%REPO%"\kodein-di-conf-metadata-6.2.1.jar;"%REPO%"\slf4j-api-1.7.26.jar;"%REPO%"\log4j-slf4j-impl-2.12.1.jar;"%REPO%"\log4j-api-2.12.1.jar;"%REPO%"\log4j-core-2.12.1.jar;"%REPO%"\config-1.3.4.jar;"%REPO%"\agrona-1.0.7.jar;"%REPO%"\Paguro-3.1.2.jar;"%REPO%"\protobuf-java-3.10.0.jar;"%REPO%"\jetty-server-11.0.14.jar;"%REPO%"\jetty-jakarta-servlet-api-5.0.2.jar;"%REPO%"\jetty-http-11.0.14.jar;"%REPO%"\jetty-util-11.0.14.jar;"%REPO%"\jetty-io-11.0.14.jar;"%REPO%"\jetty-servlet-11.0.8.jar;"%REPO%"\jetty-security-11.0.8.jar;"%REPO%"\kotlin-stdlib-1.3.72.jar;"%REPO%"\kotlin-stdlib-common-1.3.72.jar;"%REPO%"\annotations-13.0.jar;"%REPO%"\okhttp-4.9.3.jar;"%REPO%"\okio-2.8.0.jar;"%REPO%"\sample-container-0.2-SNAPSHOT.jar

set ENDORSED_DIR=
if NOT "%ENDORSED_DIR%" == "" set CLASSPATH="%BASEDIR%"\%ENDORSED_DIR%\*;%CLASSPATH%

if NOT "%CLASSPATH_PREFIX%" == "" set CLASSPATH=%CLASSPATH_PREFIX%;%CLASSPATH%

@REM Reaching here means variables are defined and arguments have been captured
:endInit

%JAVACMD% %JAVA_OPTS%  -classpath %CLASSPATH% -Dapp.name="main-kt" -Dapp.repo="%REPO%" -Dapp.home="%BASEDIR%" -Dbasedir="%BASEDIR%" de.gtarc.opaca.sample.MainKt %CMD_LINE_ARGS%
if %ERRORLEVEL% NEQ 0 goto error
goto end

:error
if "%OS%"=="Windows_NT" @endlocal
set ERROR_CODE=%ERRORLEVEL%

:end
@REM set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" goto endNT

@REM For old DOS remove the set variables from ENV - we assume they were not set
@REM before we started - at least we don't leave any baggage around
set CMD_LINE_ARGS=
goto postExec

:endNT
@REM If error code is set to 1 then the endlocal was done already in :error.
if %ERROR_CODE% EQU 0 @endlocal


:postExec

if "%FORCE_EXIT_ON_ERROR%" == "on" (
  if %ERROR_CODE% NEQ 0 exit %ERROR_CODE%
)

exit /B %ERROR_CODE%
