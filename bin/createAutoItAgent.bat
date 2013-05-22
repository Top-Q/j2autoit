@echo OFF
rem Creates AutoIt agent package in the given directory

mkdir %1%
mkdir %1%\lib
mkdir %1%\bin

rem Copy files
echo Copy files to %1%\lib...
copy %RUNNER_ROOT%\lib\j2autoit.jar %1%\lib
copy %RUNNER_ROOT%\lib\jsystemCore.jar %1%\lib
copy %RUNNER_ROOT%\lib\filetransfer.jar %1%\lib
copy %RUNNER_ROOT%\thirdparty\CommonLib\commons-codec-1.3.jar %1%\lib
copy %RUNNER_ROOT%\thirdparty\CommonLib\commons-io-1.3.1.jar %1%\lib
copy %RUNNER_ROOT%\thirdparty\lib\commons-logging-api.jar %1%\lib
copy %RUNNER_ROOT%\thirdparty\CommonLib\commons-net.jar %1%\lib
copy %RUNNER_ROOT%\thirdparty\CommonLib\jakarta-oro-2.0.8.jar %1%\lib
copy %RUNNER_ROOT%\thirdparty\lib\xml-apis.jar %1%\lib
copy %RUNNER_ROOT%\thirdparty\lib\xmlrpc-2.0.jar %1%\lib

echo Copy batch file to %1%\bin
copy %RUNNER_ROOT%\runAutoItAgent.bat %1%\bin

pause