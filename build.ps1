param(
    [string]$AndroidTools = 'C:\Program Files\Unity\Hub\Editor\6000.3.2f1\Editor\Data\PlaybackEngines\AndroidPlayer'
)
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$sdkRoot = Join-Path $AndroidTools 'SDK'
$javaRoot = Join-Path $AndroidTools 'OpenJDK'
$env:JAVA_HOME = $javaRoot
$env:PATH = (Join-Path $javaRoot 'bin') + ';' + $env:PATH
$buildTools = Join-Path $sdkRoot 'build-tools\36.0.0'
$androidJar = Join-Path $sdkRoot 'platforms\android-36\android.jar'
$parserJar = Join-Path $projectRoot 'app\libs\jsoup-1.24.1.jar'
foreach ($required in @($androidJar, $parserJar, (Join-Path $javaRoot 'bin\javac.exe'), (Join-Path $buildTools 'aapt2.exe'))) {
    if (!(Test-Path -LiteralPath $required)) { throw "Missing build dependency: $required" }
}
if ((Get-FileHash -LiteralPath $parserJar -Algorithm SHA256).Hash -ne 'FABBB4AAF7FB5340AED0613826ECD110427999D765D8E0367CEDDE2950D7097C') { throw 'Unexpected jsoup dependency checksum.' }
$buildRoot = Join-Path $projectRoot ('.local\build-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
$generated = Join-Path $buildRoot 'generated'
$classes = Join-Path $buildRoot 'classes'
$dex = Join-Path $buildRoot 'dex'
$signing = Join-Path $projectRoot '.local\signing'
$dist = Join-Path $projectRoot 'dist'
New-Item -ItemType Directory -Force -Path $generated,$classes,$dex,$signing,$dist | Out-Null
function Invoke-Checked([string]$Executable, [string[]]$Arguments) {
    & $Executable @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Build step failed: $Executable ($LASTEXITCODE)" }
}
function Assert-PackagePrivacy([string]$Package) {
    $approved = @(
        'AndroidManifest.xml','resources.arsc',
        'res/drawable/ic_launcher.xml','res/drawable/ic_launcher_foreground.xml',
        'res/drawable/icon_180.png','res/mipmap-anydpi-v26/ic_launcher.xml',
        'assets/Apache-2.0.txt','assets/Readability.js','assets/readability-LICENSE.md',
        'assets/reader.html','assets/reader.js','META-INF/jsoup/LICENSE'
    )
    $patterns = @(
        '(?i)[A-Z]:[\\/]Users[\\/]|/(?:Users|home)/[a-z0-9_.-]+/',
        '(?i)\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b',
        '-----BEGIN (?:RSA |EC |OPENSSH |ENCRYPTED )?PRIVATE KEY-----',
        '\b(?:AKIA[A-Z0-9]{16}|gh[pousr]_[A-Za-z0-9]{24,}|github_pat_[A-Za-z0-9_]{24,}|sk-(?:proj-)?[A-Za-z0-9_-]{32,})\b'
    )
    $archive = [IO.Compression.ZipFile]::OpenRead($Package)
    try {
        $names = @($archive.Entries | ForEach-Object FullName)
        foreach ($required in $approved + @('classes.dex')) {
            if ($required -cnotin $names) { throw 'Required application resource is missing.' }
        }
        foreach ($entry in $archive.Entries) {
            if ($entry.FullName -cnotin $approved -and $entry.FullName -cnotmatch '^classes(?:[2-9]|[1-9][0-9]+)?\.dex$') {
                throw 'Unexpected APK input. Review the package allowlist before including additional files.'
            }
            $stream=$entry.Open(); $memory=New-Object IO.MemoryStream
            try { $stream.CopyTo($memory); $bytes=$memory.ToArray() }
            finally { $stream.Dispose(); $memory.Dispose() }
            foreach ($encoding in @([Text.Encoding]::UTF8,[Text.Encoding]::Unicode,[Text.Encoding]::BigEndianUnicode)) {
                $text=$encoding.GetString($bytes)
                foreach ($pattern in $patterns) {
                    if ($text -cmatch $pattern) { throw 'Potential personal information or embedded credential in an APK input. Values are not logged.' }
                }
            }
        }
    } finally { $archive.Dispose() }
}
$compiled = Join-Path $buildRoot 'compiled.zip'
$resources = Join-Path $buildRoot 'resources.apk'
Invoke-Checked (Join-Path $buildTools 'aapt2.exe') @('compile','--dir',(Join-Path $projectRoot 'app\src\main\res'),'-o',$compiled)
Invoke-Checked (Join-Path $buildTools 'aapt2.exe') @('link','-o',$resources,'-I',$androidJar,'--manifest',(Join-Path $projectRoot 'app\src\main\AndroidManifest.xml'),$compiled,'-A',(Join-Path $projectRoot 'app\src\main\assets'),'--java',$generated,'--min-sdk-version','26','--target-sdk-version','35','--version-code','87','--version-name','0.1.86')
$sources = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'app\src\main\java'),$generated -Recurse -Filter '*.java' | ForEach-Object FullName)
Invoke-Checked (Join-Path $javaRoot 'bin\javac.exe') (@('--release','17','-encoding','UTF-8','-classpath',"$androidJar;$parserJar",'-d',$classes) + $sources)
$applicationJar = Join-Path $buildRoot 'app.jar'
Invoke-Checked (Join-Path $javaRoot 'bin\jar.exe') @('--create','--file',$applicationJar,'-C',$classes,'.')
# A single base Java implementation is dexed; multi-release desktop variants are excluded.
Add-Type -AssemblyName System.IO.Compression.FileSystem
$baseJar = Join-Path $buildRoot 'jsoup-base.jar'
$inputZip = [IO.Compression.ZipFile]::OpenRead($parserJar)
$outputZip = [IO.Compression.ZipFile]::Open($baseJar,[IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($entry in $inputZip.Entries) {
        if ($entry.FullName.StartsWith('META-INF/versions/') -or $entry.FullName -eq 'module-info.class' -or $entry.FullName.EndsWith('/')) { continue }
        $copy = $outputZip.CreateEntry($entry.FullName)
        $reader = $entry.Open(); $writer = $copy.Open()
        try { $reader.CopyTo($writer) } finally { $reader.Dispose(); $writer.Dispose() }
    }
} finally { $inputZip.Dispose(); $outputZip.Dispose() }
Invoke-Checked (Join-Path $buildTools 'd8.bat') @('--min-api','26','--lib',$androidJar,'--output',$dex,$applicationJar,$baseJar)
$unsigned = Join-Path $buildRoot 'unsigned.apk'
Copy-Item -LiteralPath $resources -Destination $unsigned
$apkZip = [IO.Compression.ZipFile]::Open($unsigned,[IO.Compression.ZipArchiveMode]::Update)
try {
    foreach ($file in Get-ChildItem -LiteralPath $dex -Filter '*.dex') { [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($apkZip,$file.FullName,$file.Name) | Out-Null }
    $library = [IO.Compression.ZipFile]::OpenRead($baseJar)
    try {
        foreach ($entry in $library.Entries) {
            if ($entry.FullName.EndsWith('.class') -or ($entry.FullName.StartsWith('META-INF/') -and $entry.FullName -ne 'META-INF/jsoup/LICENSE') -or $entry.FullName.EndsWith('/')) { continue }
            $copy = $apkZip.CreateEntry($entry.FullName); $reader=$entry.Open();$writer=$copy.Open()
            try { $reader.CopyTo($writer) } finally { $reader.Dispose();$writer.Dispose() }
        }
    } finally { $library.Dispose() }
} finally { $apkZip.Dispose() }
# Refuse accidental local credential files or installed-app data in a distributable APK.
$packageCheck = [IO.Compression.ZipFile]::OpenRead($unsigned)
try {
    foreach ($entry in $packageCheck.Entries) {
        if ($entry.FullName -match '(?i)(^|/)(shared_prefs|databases|\.local|\.git)/|\.(jks|keystore|pem|key|p12|pfx)$|(^|/)\.env($|\.)') {
            throw 'Refusing to package local credentials, private keys or installed-app data.'
        }
    }
} finally { $packageCheck.Dispose() }
Assert-PackagePrivacy $unsigned
# Keep build times and filesystem attributes out of the distributed archive.
$metadata = [IO.Compression.ZipFile]::Open($unsigned,[IO.Compression.ZipArchiveMode]::Update)
try {
    foreach ($entry in $metadata.Entries) {
        $entry.LastWriteTime = [DateTimeOffset]::new(1980,1,1,0,0,0,[TimeSpan]::Zero)
        $entry.ExternalAttributes = 0
    }
} finally { $metadata.Dispose() }
$aligned = Join-Path $buildRoot 'aligned.apk'
Invoke-Checked (Join-Path $buildTools 'zipalign.exe') @('-f','-p','4',$unsigned,$aligned)
$keystore = Join-Path $signing 'xo-classic-development.jks'
if (!(Test-Path -LiteralPath $keystore)) {
    Invoke-Checked (Join-Path $javaRoot 'bin\keytool.exe') @('-genkeypair','-keystore',$keystore,'-storepass','android','-keypass','android','-alias','xoclassic','-dname','CN=XO Classic Personal Development','-keyalg','RSA','-keysize','2048','-validity','10000')
}
$apk = Join-Path $dist 'xo-classic-0.1.86.apk'
Invoke-Checked (Join-Path $buildTools 'apksigner.bat') @('sign','--ks',$keystore,'--ks-key-alias','xoclassic','--ks-pass','pass:android','--key-pass','pass:android','--v1-signing-enabled','false','--v2-signing-enabled','true','--v3-signing-enabled','true','--v4-signing-enabled','false','--out',$apk,$aligned)
Invoke-Checked (Join-Path $buildTools 'apksigner.bat') @('verify','--verbose','--print-certs',$apk)
Invoke-Checked (Join-Path $buildTools 'zipalign.exe') @('-c','-p','4',$apk)
$hash = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
[IO.File]::WriteAllText((Join-Path $dist 'SHA256SUMS.txt'), "$hash  xo-classic-0.1.86.apk`n")
Write-Output "APK ready: $apk"

