# Saves a screenshot of the connected device to build\shots\<name>.png and prints the path.
param([string]$Name = "shot")
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$dir = Join-Path (Split-Path -Parent $PSScriptRoot) "build\shots"
New-Item -ItemType Directory -Force $dir | Out-Null
$out = Join-Path $dir "$Name.png"
& $adb shell screencap -p /sdcard/foxshot.png | Out-Null
& $adb pull /sdcard/foxshot.png $out | Out-Null
& $adb shell rm /sdcard/foxshot.png | Out-Null
$out
