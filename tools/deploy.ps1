# Installs the debug APK (unless -NoInstall) and starts the browser with debug extras, e.g.
#   tools\deploy.ps1 -Extras '--es style holo --es brightness dark' -Shot holo
param([string]$Extras = "", [string]$Shot = "", [int]$Wait = 6, [switch]$NoInstall, [string]$Url = "")
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$root = Split-Path -Parent $PSScriptRoot
if (-not $NoInstall) { & $adb install -r "$root\app\build\outputs\apk\debug\app-debug.apk" | Select-Object -Last 1 }
$data = if ($Url) { "-d $Url -a android.intent.action.VIEW" } else { "" }
& $adb shell "am start -n com.disgusty.foxtalgia.debug/com.disgusty.foxtalgia.ui.BrowserActivity $data $Extras" | Out-Null
Start-Sleep $Wait
if ($Shot) { & "$PSScriptRoot\shot.ps1" $Shot }
