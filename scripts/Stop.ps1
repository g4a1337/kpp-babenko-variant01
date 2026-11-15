. "$PSScriptRoot\Environment.ps1"
foreach ($kind in @('client','tomcat')) {
    $pidFile = Join-Path $RuntimeRoot ($kind+'.pid')
    if (Test-Path -LiteralPath $pidFile) {
        $processId = [int](Get-Content -LiteralPath $pidFile)
        $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
        if ($process -and $process.Path -like "$env:JAVA_HOME\*") { Stop-Process -Id $processId }
        Remove-Item -LiteralPath $pidFile
    }
}
if ($Settings) {
    $env:MYSQL_PWD = $Settings.databasePassword
    try { & "$MySqlHome\bin\mysqladmin.exe" --host=127.0.0.1 "--port=$($Settings.mysqlPort)" --user=root shutdown 2>$null } finally { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue }
}
Write-Host 'Coursework processes stopped.'
