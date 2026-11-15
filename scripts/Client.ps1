param([string]$ScreenshotDirectory='')
. "$PSScriptRoot\Environment.ps1"
$pidFile = Join-Path $RuntimeRoot 'client.pid'
if (Test-Path -LiteralPath $pidFile) {
    $existing = Get-Process -Id ([int](Get-Content -LiteralPath $pidFile)) -ErrorAction SilentlyContinue
    if ($existing -and $existing.Path -eq "$env:JAVA_HOME\bin\java.exe") {
        Write-Host 'JavaFX client is already running.'
        return
    }
}
$launch = Join-Path $ProjectRoot 'client\target\app'
if (-not (Test-Path -LiteralPath "$launch\it-support-client.jar")) { throw 'Client distribution missing. Run scripts\Build.ps1.' }
$jfx = Join-Path $ProjectRoot 'client\target\javafx'
$argsJava = @('-Dfile.encoding=UTF-8',"`"-Dapi.baseUrl=$env:API_BASE_URL`"")
if ($ScreenshotDirectory) { $argsJava += "`"-Dapp.screenshotDir=$ScreenshotDirectory`"" }
$argsJava += @('--module-path',"`"$jfx`"",'--add-modules','javafx.controls,javafx.swing','-jar',"`"$launch\it-support-client.jar`"")
$clientProcess = Start-Process -FilePath "$env:JAVA_HOME\bin\java.exe" -ArgumentList $argsJava -WindowStyle Hidden -PassThru -RedirectStandardOutput "$RuntimeRoot\client.stdout.log" -RedirectStandardError "$RuntimeRoot\client.stderr.log"
$clientProcess.Id | Set-Content -LiteralPath "$RuntimeRoot\client.pid"
Write-Host 'JavaFX client started.'
