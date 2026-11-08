$ErrorActionPreference = 'Stop'
$proj = $PSScriptRoot
$src  = Join-Path $proj 'project'
$out  = Join-Path $proj 'out'
$versionName = if ($env:VERSION_NAME) { $env:VERSION_NAME } else { '2.0' }
$versionCode = if ($env:VERSION_CODE) { [int]$env:VERSION_CODE } else { 11 }
$sdk = $env:ANDROID_SDK_ROOT
if (-not $sdk) { $sdk = $env:ANDROID_HOME }
if (-not $sdk) { $sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$buildToolsDir = Get-ChildItem (Join-Path $sdk 'build-tools') -Directory |
    Where-Object { $_.Name -match '^\d+(\.\d+)+$' } |
    Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
if (-not $buildToolsDir) { throw "Android build-tools not found under $sdk" }
$bt = $buildToolsDir.FullName
# Sirf stable platform dirs (android-34 / android-34.0) - ext/beta dirs walay android.jar skip
$platform = Get-ChildItem (Join-Path $sdk 'platforms') -Directory |
    ForEach-Object {
        if ($_.Name -match '^android-(\d+(?:\.\d+)?)$' -and (Test-Path (Join-Path $_.FullName 'android.jar'))) {
            $raw = $Matches[1]
            $ver = if ($raw.Contains('.')) { $raw } else { "$raw.0" }
            [pscustomobject]@{ Dir = $_; Ver = [version]$ver }
        }
    } |
    Sort-Object Ver -Descending |
    Select-Object -First 1 -ExpandProperty Dir
if (-not $platform) { throw "Android platform android.jar not found under $sdk" }
$androidJar = Join-Path $platform.FullName 'android.jar'
$javaHome = $env:JAVA_HOME
if (-not $javaHome) {
    $studioJbr = 'C:\Program Files\Android\Android Studio\jbr'
    if (Test-Path (Join-Path $studioJbr 'bin\javac.exe')) { $javaHome = $studioJbr }
}
if (-not $javaHome) { throw 'Set JAVA_HOME to a JDK installation' }
$javac = Join-Path $javaHome 'bin\javac.exe'
$jar = Join-Path $javaHome 'bin\jar.exe'
$keytool = Join-Path $javaHome 'bin\keytool.exe'
if (-not (Test-Path $javac) -or -not (Test-Path $jar)) {
    throw "JDK tools not found under $javaHome"
}

$env:JAVA_HOME = $javaHome
$env:PATH = "$javaHome\bin;$env:PATH"
$build = Join-Path $out 'build'
foreach ($dir in @($build, (Join-Path $out 'gen'), (Join-Path $out 'classes'), (Join-Path $out 'dex'))) {
    if (Test-Path $dir) { Remove-Item $dir -Recurse -Force }
}
New-Item -ItemType Directory -Force -Path $out | Out-Null
New-Item -ItemType Directory -Force -Path $build, (Join-Path $out 'gen'), (Join-Path $out 'classes'), (Join-Path $out 'dex') | Out-Null

Write-Host "[1/6] aapt2 compile resources"
& (Join-Path $bt 'aapt2.exe') compile --dir (Join-Path $src 'res') -o (Join-Path $build 'res.zip')
if ($LASTEXITCODE -ne 0) { throw 'aapt2 compile failed' }

Write-Host "[2/6] aapt2 link"
& (Join-Path $bt 'aapt2.exe') link `
    -o (Join-Path $build 'base.apk') `
    -I $androidJar `
    --manifest (Join-Path $src 'AndroidManifest.xml') `
    --java (Join-Path $out 'gen') `
    --min-sdk-version 21 --target-sdk-version 34 `
    --version-code $versionCode --version-name $versionName `
    --auto-add-overlay `
    (Join-Path $build 'res.zip')
if ($LASTEXITCODE -ne 0) { throw 'aapt2 link failed' }

Write-Host "[3/6] javac"
$javaFiles = @(Get-ChildItem (Join-Path $src 'src') -Recurse -Filter *.java | ForEach-Object { $_.FullName })
$javaFiles += @(Get-ChildItem (Join-Path $out 'gen') -Recurse -Filter *.java | ForEach-Object { $_.FullName })
& $javac -nowarn -encoding UTF-8 -source 8 -target 8 -classpath $androidJar -d (Join-Path $out 'classes') $javaFiles
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }

Write-Host "[4/6] dex"
& $jar --create --file (Join-Path $build 'classes.jar') -C (Join-Path $out 'classes') .
Push-Location $bt
& cmd /c "d8.bat --release --lib `"$androidJar`" --min-api 21 --output `"$out\dex`" `"$build\classes.jar`""
$code = $LASTEXITCODE
Pop-Location
if ($code -ne 0) { throw 'd8 failed' }

Write-Host "[5/6] add dex + zipalign"
Copy-Item (Join-Path $build 'base.apk') (Join-Path $build 'withdex.apk') -Force
& $jar --update --file (Join-Path $build 'withdex.apk') `
    -C (Join-Path $out 'dex') 'classes.dex'
if ($LASTEXITCODE -ne 0) { throw 'add_dex failed' }
& (Join-Path $bt 'zipalign.exe') -f -p 4 (Join-Path $build 'withdex.apk') (Join-Path $build 'aligned.apk')
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed' }

Write-Host "[6/6] sign"
if ($env:KEYSTORE_PATH) {
    if (-not (Test-Path $env:KEYSTORE_PATH)) {
        throw "KEYSTORE_PATH is set but the file was not found: $($env:KEYSTORE_PATH)"
    }
    $ks = $env:KEYSTORE_PATH
    $ksPass = $env:KEYSTORE_PASS
    $keyAlias = $env:KEY_ALIAS
    $keyPass = $env:KEY_PASS
    if (-not $ksPass -or -not $keyAlias -or -not $keyPass) {
        throw 'KEYSTORE_PASS, KEY_ALIAS and KEY_PASS must be set when KEYSTORE_PATH is provided'
    }
    Write-Host "Using provided keystore: $ks (alias $keyAlias)"
} else {
    $ks = Join-Path $out 'pulse-debug.keystore'
    if (-not (Test-Path $ks)) {
        $prev = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        & $keytool -genkeypair -keystore $ks -storepass android -keypass android `
            -alias pulse -keyalg RSA -keysize 2048 -validity 10000 `
            -dname "CN=Naat Player, O=Naat, C=PK" 2>&1 | Out-Null
        $ErrorActionPreference = $prev
    }
    $ksPass = 'android'
    $keyAlias = 'pulse'
    $keyPass = 'android'
    Write-Host "No keystore provided - auto-generated debug keystore at $ks"
}
$final = Join-Path $proj "NaatPlayer-$versionName.apk"
$prev = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
& (Join-Path $bt 'apksigner.bat') sign --ks $ks --ks-pass "pass:$ksPass" --key-pass "pass:$keyPass" `
    --ks-key-alias $keyAlias --out $final (Join-Path $build 'aligned.apk') 2>&1 | Out-Null
$signCode = $LASTEXITCODE
$ErrorActionPreference = $prev
if ($signCode -ne 0) { throw 'apksigner failed' }

$prev = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
$verifyOut = & (Join-Path $bt 'apksigner.bat') verify --print-certs $final 2>&1
$verifyCode = $LASTEXITCODE
$ErrorActionPreference = $prev
$verifyOut | Select-Object -First 4
if ($verifyCode -ne 0) { throw 'apksigner verify failed' }
$sizeKb = [math]::Round((Get-Item $final).Length / 1024, 1)
Write-Host "BUILD OK -> $final ($sizeKb KB)"
Write-Host "VERSION=$versionName CODE=$versionCode"
Write-Host "APK_PATH=$final"
