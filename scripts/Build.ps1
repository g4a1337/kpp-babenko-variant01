. "$PSScriptRoot\Environment.ps1"
foreach ($module in @('server','client')) {
    & "$MavenHome\bin\mvn.cmd" "-Dmaven.repo.local=$ToolsRoot\m2" -f "$ProjectRoot\$module\pom.xml" clean verify
    if ($LASTEXITCODE -ne 0) { throw "Build failed: $module" }
}
