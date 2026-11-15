$ErrorActionPreference = 'Stop'
$OutputEncoding = [Text.UTF8Encoding]::new($false)
$ProjectRoot = Split-Path $PSScriptRoot -Parent
$ToolsRoot = Join-Path $ProjectRoot '.tools'
$RuntimeRoot = Join-Path $ProjectRoot '.runtime'
function Find-ToolDirectory([string]$Pattern) {
    $found = Get-ChildItem -LiteralPath $ToolsRoot -Directory -Filter $Pattern -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $found) { throw "Tool $Pattern missing. Run scripts\Setup.ps1 first." }
    return $found.FullName
}
$env:JAVA_HOME = Find-ToolDirectory 'jdk-21*'
$MavenHome = Find-ToolDirectory 'apache-maven-*'
$TomcatHome = Find-ToolDirectory 'apache-tomcat-9*'
$MySqlHome = Find-ToolDirectory 'mysql-*-winx64'
$env:PATH = "$env:JAVA_HOME\bin;$MavenHome\bin;$MySqlHome\bin;$env:PATH"
$env:MAVEN_OPTS = '-Dfile.encoding=UTF-8'
New-Item -ItemType Directory -Force -Path $RuntimeRoot | Out-Null
$SettingsFile = Join-Path $RuntimeRoot 'settings.json'
if (Test-Path -LiteralPath $SettingsFile) {
    $Settings = Get-Content -LiteralPath $SettingsFile -Raw | ConvertFrom-Json
    $env:DB_URL = "jdbc:mysql://127.0.0.1:$($Settings.mysqlPort)/it_support?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&serverTimezone=UTC"
    $env:DB_USER = 'coursework'
    $env:DB_PASSWORD = $Settings.databasePassword
    $env:API_BASE_URL = "http://127.0.0.1:$($Settings.httpPort)/it-support-server/api"
}
