$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$ProjectRoot = Split-Path $PSScriptRoot -Parent
$tools = Join-Path $ProjectRoot '.tools'
New-Item -ItemType Directory -Force -Path $tools | Out-Null
$cache = Join-Path $tools 'downloads'
New-Item -ItemType Directory -Force -Path $cache | Out-Null
function Download-Zip($url,$name,$pattern,$hashUrl,$algorithm) {
    if (Get-ChildItem -LiteralPath $tools -Directory -Filter $pattern) { return }
    $zip = Join-Path $cache ($name+'.zip')
    Write-Host "Downloading $name..."
    Invoke-WebRequest -Uri $url -OutFile $zip
    if ($hashUrl) {
        $expected = ((Invoke-WebRequest -Uri $hashUrl).Content.Trim() -split '\s+')[0]
        if ((Get-FileHash -LiteralPath $zip -Algorithm $algorithm).Hash -ne $expected) { throw "$name checksum mismatch" }
    }
    Expand-Archive -LiteralPath $zip -DestinationPath $tools -Force
}
if (-not (Get-ChildItem -LiteralPath $tools -Directory -Filter 'jdk-21*')) {
    $jdk = (Invoke-RestMethod 'https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse')[0].binary.package
    $zip = Join-Path $cache 'jdk.zip'
    Invoke-WebRequest -Uri $jdk.link -OutFile $zip
    if ((Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash -ne $jdk.checksum) { throw 'JDK checksum mismatch' }
    Expand-Archive -LiteralPath $zip -DestinationPath $tools -Force
}
Download-Zip 'https://dlcdn.apache.org/maven/maven-3/3.9.16/binaries/apache-maven-3.9.16-bin.zip' 'Maven' 'apache-maven-*' 'https://downloads.apache.org/maven/maven-3/3.9.16/binaries/apache-maven-3.9.16-bin.zip.sha512' 'SHA512'
Download-Zip 'https://dlcdn.apache.org/tomcat/tomcat-9/v9.0.124/bin/apache-tomcat-9.0.124-windows-x64.zip' 'Tomcat' 'apache-tomcat-9*' 'https://downloads.apache.org/tomcat/tomcat-9/v9.0.124/bin/apache-tomcat-9.0.124-windows-x64.zip.sha512' 'SHA512'
Download-Zip 'https://cdn.mysql.com/Downloads/MySQL-8.4/mysql-8.4.13-winx64.zip' 'MySQL' 'mysql-*-winx64' $null $null
. "$PSScriptRoot\Environment.ps1"
& "$env:JAVA_HOME\bin\java.exe" -version
& "$MavenHome\bin\mvn.cmd" -version
Write-Host 'Portable tools are ready. Run scripts\Start.ps1.'
