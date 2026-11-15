param([switch]$NoClient,[switch]$SkipBuild)
. "$PSScriptRoot\Environment.ps1"
function Wait-Endpoint($uri) {
    for ($i=0; $i -lt 60; $i++) {
        try { return Invoke-RestMethod -Uri $uri -TimeoutSec 2 } catch { Start-Sleep -Milliseconds 500 }
    }
    throw "Server did not become ready: $uri. Check .runtime logs."
}
function Invoke-MySql([string]$sql,[string]$user='root',[switch]$WithoutPassword) {
    $env:MYSQL_PWD = if ($WithoutPassword) { '' } else { $Settings.databasePassword }
    try {
        $sql | & "$MySqlHome\bin\mysql.exe" --host=127.0.0.1 "--port=$($Settings.mysqlPort)" "--user=$user" --default-character-set=utf8mb4
        if ($LASTEXITCODE -ne 0) { throw 'MySQL operation failed.' }
    } finally { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue }
}
if (-not $Settings) {
    $Settings = [pscustomobject]@{ mysqlPort=3307; httpPort=8080; databasePassword=([guid]::NewGuid().ToString('N')+[guid]::NewGuid().ToString('N')) }
    $Settings | ConvertTo-Json | Set-Content -LiteralPath $SettingsFile -Encoding utf8
    . "$PSScriptRoot\Environment.ps1"
}
# Release the client JARs before a rebuild and always redeploy the current WAR.
foreach ($kind in @('client','tomcat')) {
    $pidFile = Join-Path $RuntimeRoot ($kind+'.pid')
    if (Test-Path -LiteralPath $pidFile) {
        $managed = Get-Process -Id ([int](Get-Content -LiteralPath $pidFile)) -ErrorAction SilentlyContinue
        if ($managed -and $managed.Path -eq "$env:JAVA_HOME\bin\java.exe") {
            Stop-Process -Id $managed.Id
            Wait-Process -Id $managed.Id -Timeout 15 -ErrorAction SilentlyContinue
        }
        Remove-Item -LiteralPath $pidFile
    }
}
$data = Join-Path $RuntimeRoot 'mysql-data'
$fresh = -not (Test-Path -LiteralPath (Join-Path $data 'mysql'))
$mysqlConfig = Join-Path $RuntimeRoot 'my.ini'
$baseSlash = $MySqlHome.Replace('\','/')
$dataSlash = $data.Replace('\','/')
@"
[mysqld]
basedir=$baseSlash
datadir=$dataSlash
port=$($Settings.mysqlPort)
bind-address=127.0.0.1
mysqlx=OFF
character-set-server=utf8mb4
collation-server=utf8mb4_unicode_ci
"@ | ForEach-Object { [IO.File]::WriteAllText($mysqlConfig,$_,[Text.UTF8Encoding]::new($false)) }
if ($fresh) {
    & "$MySqlHome\bin\mysqld.exe" "--defaults-file=$mysqlConfig" --initialize-insecure
    if ($LASTEXITCODE -ne 0) { throw 'MySQL initialization failed.' }
}
$mysqlPid = Join-Path $RuntimeRoot 'mysql.pid'
$running = $false
if (Test-Path -LiteralPath $mysqlPid) {
    $existing = Get-Process -Id ([int](Get-Content -LiteralPath $mysqlPid)) -ErrorAction SilentlyContinue
    $running = $existing -and $existing.Path -eq "$MySqlHome\bin\mysqld.exe"
}
if (-not $running) {
    $mysqlProcess = Start-Process -FilePath "$MySqlHome\bin\mysqld.exe" -ArgumentList "`"--defaults-file=$mysqlConfig`" --console" -WindowStyle Hidden -PassThru -RedirectStandardOutput "$RuntimeRoot\mysql.stdout.log" -RedirectStandardError "$RuntimeRoot\mysql.stderr.log"
    $mysqlProcess.Id | Set-Content -LiteralPath $mysqlPid
}
for ($i=0;$i -lt 60;$i++) {
    $tcp = [Net.Sockets.TcpClient]::new()
    try { $tcp.Connect('127.0.0.1',$Settings.mysqlPort); break } catch { Start-Sleep -Milliseconds 500 } finally { $tcp.Dispose() }
}
$bootstrapMarker = Join-Path $RuntimeRoot 'database-ready.txt'
if (-not (Test-Path -LiteralPath $bootstrapMarker)) {
    $secret = $Settings.databasePassword
    $env:MYSQL_PWD = $secret
    try {
        & "$MySqlHome\bin\mysql.exe" --host=127.0.0.1 "--port=$($Settings.mysqlPort)" --user=root --execute='SELECT 1' *> $null
        $rootConfigured = $LASTEXITCODE -eq 0
    } finally { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue }
    if (-not $rootConfigured) { Invoke-MySql "ALTER USER 'root'@'localhost' IDENTIFIED BY '$secret';" -WithoutPassword }
    Invoke-MySql (Get-Content -LiteralPath "$ProjectRoot\database.sql" -Raw -Encoding UTF8)
    Invoke-MySql "CREATE USER IF NOT EXISTS 'coursework'@'127.0.0.1' IDENTIFIED BY '$secret'; GRANT SELECT,INSERT,UPDATE,DELETE ON it_support.* TO 'coursework'@'127.0.0.1';"
    'Database schema and local account initialized.' | Set-Content -LiteralPath $bootstrapMarker
}
if (-not $SkipBuild) { & "$PSScriptRoot\Build.ps1" }
$catalinaBase = Join-Path $RuntimeRoot 'tomcat'
foreach ($dir in @('conf','logs','temp','work','webapps')) { New-Item -ItemType Directory -Force -Path (Join-Path $catalinaBase $dir) | Out-Null }
if (-not (Test-Path -LiteralPath "$catalinaBase\conf\server.xml")) {
    Copy-Item -Path "$TomcatHome\conf\*" -Destination "$catalinaBase\conf" -Recurse
    $xml = Get-Content -LiteralPath "$catalinaBase\conf\server.xml" -Raw
    $xml = $xml.Replace('port="8005"','port="-1"').Replace('port="8080"',"address=`"127.0.0.1`" port=`"$($Settings.httpPort)`"")
    $xml | Set-Content -LiteralPath "$catalinaBase\conf\server.xml" -Encoding utf8
}
$tomcatRunning = $false
if (Test-Path -LiteralPath "$RuntimeRoot\tomcat.pid") {
    $existing = Get-Process -Id ([int](Get-Content -LiteralPath "$RuntimeRoot\tomcat.pid")) -ErrorAction SilentlyContinue
    $tomcatRunning = $existing -and $existing.Path -eq "$env:JAVA_HOME\bin\java.exe"
}
if (-not $tomcatRunning) {
    Copy-Item -LiteralPath "$ProjectRoot\server\target\it-support-server.war" -Destination "$catalinaBase\webapps\it-support-server.war" -Force
    $argsJava = @('-Dfile.encoding=UTF-8',"`"-Dcatalina.home=$TomcatHome`"","`"-Dcatalina.base=$catalinaBase`"","`"-Djava.io.tmpdir=$catalinaBase\temp`"",'-cp',"`"$TomcatHome\bin\bootstrap.jar;$TomcatHome\bin\tomcat-juli.jar`"",'org.apache.catalina.startup.Bootstrap','start')
    $serverProcess = Start-Process -FilePath "$env:JAVA_HOME\bin\java.exe" -ArgumentList $argsJava -WindowStyle Hidden -PassThru -RedirectStandardOutput "$RuntimeRoot\tomcat.stdout.log" -RedirectStandardError "$RuntimeRoot\tomcat.stderr.log"
    $serverProcess.Id | Set-Content -LiteralPath "$RuntimeRoot\tomcat.pid"
}
$null = Wait-Endpoint "$env:API_BASE_URL/users"
Write-Host "Server ready: $env:API_BASE_URL"
if (-not $NoClient) { & "$PSScriptRoot\Client.ps1" }
