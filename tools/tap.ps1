# Taps the first on-screen element whose text contains $Text (uiautomator dump). Prints the bounds or "not found".
param([Parameter(Mandatory = $true)][string]$Text)
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb shell uiautomator dump /sdcard/fox_ui.xml | Out-Null
$xml = [string](& $adb shell cat /sdcard/fox_ui.xml)
$m = [regex]::Matches($xml, '<node [^>]*?text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
foreach ($pass in 0, 1) {
foreach ($n in $m) {
    $v = $n.Groups[1].Value
    if (($pass -eq 0 -and $v -eq $Text) -or ($pass -eq 1 -and $v -like "*$Text*")) {
        $x = ([int]$n.Groups[2].Value + [int]$n.Groups[4].Value) / 2
        $y = ([int]$n.Groups[3].Value + [int]$n.Groups[5].Value) / 2
        & $adb shell input tap $x $y
        "tapped '$($n.Groups[1].Value)' at $x,$y"
        exit 0
    }
}
}
"not found: $Text"
exit 1
