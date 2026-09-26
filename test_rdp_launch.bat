@echo off
setlocal
if "%~1"=="" (
  echo Usage: test_rdp_launch.bat ^<tailscale-host^> ^<adb-device-id^>
  exit /b 2
)
if "%~2"=="" (
  echo Usage: test_rdp_launch.bat ^<tailscale-host^> ^<adb-device-id^>
  exit /b 2
)
set "HOST=%~1"
set "DEVICE_ID=%~2"
set "ADB_PATH=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
set "RDP_FILE=%TEMP%\cloud.rdp"
(
  echo full address:s:%HOST%:3389
  echo username:s:RDP
  echo prompt for credentials:i:0
) > "%RDP_FILE%"
"%ADB_PATH%" -s "%DEVICE_ID%" push "%RDP_FILE%" /sdcard/cloud.rdp || exit /b 1
"%ADB_PATH%" -s "%DEVICE_ID%" shell am start -a android.intent.action.VIEW -d "file:///sdcard/cloud.rdp" -t "application/rdp" -n "com.microsoft.rdc.androidx/com.microsoft.windowsapp.ui.RdpFileUriLaunchActivity"
endlocal
