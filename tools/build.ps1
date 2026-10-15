# Builds Foxtalgia and prints only errors. Usage: tools\build.ps1 [task] [abis]
param([string]$Task = "assembleDebug", [string]$Abis = "x86_64")
$env:JAVA_HOME = (Get-ChildItem "C:\Program Files\Microsoft" -Filter "jdk-21*" | Select-Object -First 1).FullName
$root = Split-Path -Parent $PSScriptRoot
$log = Join-Path $root "build\last-build.txt"
New-Item -ItemType Directory -Force (Split-Path $log) | Out-Null
Push-Location $root
try {
    [Environment]::CurrentDirectory = $root
    cmd /c "cd /d $root && .\gradlew.bat $Task -Pfoxtalgia.abis=$Abis --console=plain > $log 2>&1"
    $code = $LASTEXITCODE
} finally {
    Pop-Location
}
Select-String -Path $log -Pattern 'error:|\.java:\d+: |What went wrong|BUILD (SUCCESSFUL|FAILED)|Lint found|Errors found' | Select-Object -First 120 | ForEach-Object { $_.Line }
exit $code
