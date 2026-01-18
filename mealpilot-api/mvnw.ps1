[CmdletBinding()]
param(
  [Parameter(ValueFromRemainingArguments = $true)]
  [string[]]$Args
)

$ErrorActionPreference = 'Stop'

function Get-JavaMajorVersion {
  param([Parameter(Mandatory = $true)][string]$JavaExe)
  try {
    $jdkHome = Split-Path (Split-Path $JavaExe -Parent) -Parent
    $releasePath = Join-Path $jdkHome 'release'
    if (Test-Path $releasePath) {
      $release = Get-Content -Raw $releasePath
      $m = [Regex]::Match($release, 'JAVA_VERSION="(?<v>\d+)(?:\.(?<minor>\d+))?')
      if ($m.Success) {
        return [int]$m.Groups['v'].Value
      }
    }
  } catch {
    return $null
  }

  return $null
}

function Resolve-JavaCommand {
  $candidates = New-Object System.Collections.Generic.List[string]

  if ($env:JAVA_HOME) {
    $candidates.Add((Join-Path $env:JAVA_HOME 'bin\java.exe'))
  }

  foreach ($envName in @('JAVA_HOME_21_X64','JAVA_HOME_21','JDK_HOME_21_X64','JDK_HOME_21')) {
    $envItem = Get-Item "Env:$envName" -ErrorAction SilentlyContinue
    if ($envItem -and $envItem.Value) {
      $candidates.Add((Join-Path $envItem.Value 'bin\java.exe'))
    }
  }

  foreach ($root in @('C:\Program Files\Java', 'C:\Program Files\Eclipse Adoptium')) {
    if (Test-Path $root) {
      Get-ChildItem $root -Directory -ErrorAction SilentlyContinue |
        ForEach-Object { Join-Path $_.FullName 'bin\java.exe' } |
        ForEach-Object { $candidates.Add($_) }
    }
  }

  $resolved = Get-Command java -ErrorAction SilentlyContinue
  if ($resolved) {
    $candidates.Add($resolved.Source)
  }

  $unique = $candidates | Where-Object { $_ -and (Test-Path $_) } | Select-Object -Unique
  if (-not $unique -or $unique.Count -eq 0) {
    throw "Java was not found. Install a JDK 21+ and set JAVA_HOME (or add java to PATH)."
  }

  $scored = $unique | ForEach-Object {
    [PSCustomObject]@{ Path = $_; Major = (Get-JavaMajorVersion -JavaExe $_) }
  } | Where-Object { $_.Major }

  $preferred21 = $scored | Where-Object { $_.Major -eq 21 } | Select-Object -First 1
  if ($preferred21) { return $preferred21.Path }

  $preferred = $scored | Where-Object { $_.Major -ge 21 } | Sort-Object Major -Descending | Select-Object -First 1
  if ($preferred) { return $preferred.Path }

  # Fallback: pick highest available, but it may fail the project's Maven Enforcer rule.
  return ($scored | Sort-Object Major -Descending | Select-Object -First 1).Path
}

$javaCmd = Resolve-JavaCommand

$javaMajor = Get-JavaMajorVersion -JavaExe $javaCmd
if (-not $javaMajor) {
  throw "Could not determine Java version for: $javaCmd"
}

$projectBaseDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$wrapperPropsPath = Join-Path $projectBaseDir '.mvn\wrapper\maven-wrapper.properties'

if (-not (Test-Path $wrapperPropsPath)) {
  throw "Maven Wrapper properties not found: $wrapperPropsPath"
}

$props = Get-Content -Raw $wrapperPropsPath | ConvertFrom-StringData
$distributionUrl = $props.distributionUrl
if (-not $distributionUrl) {
  throw "distributionUrl is missing in $wrapperPropsPath"
}

if ($env:MVNW_REPOURL) {
  $distributionUrl = "$($env:MVNW_REPOURL)/org/apache/maven/" + ($distributionUrl -replace '^.*/org/apache/maven/','')
}

$distributionFileName = ($distributionUrl -replace '^.*/','')
$distributionNameMain = ($distributionFileName -replace '\.[^.]*$','' -replace '-bin$','')
if (-not $distributionNameMain) {
  throw "distributionUrl is not valid: $distributionUrl"
}

$m2Path = if ($env:MAVEN_USER_HOME) { $env:MAVEN_USER_HOME } else { Join-Path $HOME '.m2' }
$wrapperDists = Join-Path $m2Path 'wrapper\dists'
New-Item -ItemType Directory -Force -Path $wrapperDists | Out-Null

$distributionParent = Join-Path $wrapperDists $distributionNameMain
$hashBytes = [System.Security.Cryptography.SHA256]::Create().ComputeHash([Text.Encoding]::UTF8.GetBytes($distributionUrl))
$hash = ($hashBytes | ForEach-Object { $_.ToString('x2') }) -join ''
$mavenHome = Join-Path $distributionParent $hash

if (-not (Test-Path $mavenHome -PathType Container)) {
  $tmpDir = Join-Path ([System.IO.Path]::GetTempPath()) ([System.Guid]::NewGuid().ToString('n'))
  New-Item -ItemType Directory -Force -Path $tmpDir | Out-Null
  try {
    $zipPath = Join-Path $tmpDir $distributionFileName
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    Invoke-WebRequest -Uri $distributionUrl -OutFile $zipPath

    Expand-Archive -Path $zipPath -DestinationPath $tmpDir -Force

    $extractedRoot = Get-ChildItem -Path $tmpDir -Directory | Where-Object {
      (Test-Path (Join-Path $_.FullName 'bin\m2.conf')) -and (Test-Path (Join-Path $_.FullName 'boot'))
    } | Select-Object -First 1

    if (-not $extractedRoot) {
      throw "Could not locate Maven distribution directory after extracting $zipPath"
    }

    New-Item -ItemType Directory -Force -Path $distributionParent | Out-Null
    Move-Item -Path $extractedRoot.FullName -Destination $mavenHome
  } finally {
    if (Test-Path $tmpDir) { Remove-Item -Recurse -Force $tmpDir | Out-Null }
  }
}

$classworldsJar = Get-ChildItem -Path (Join-Path $mavenHome 'boot') -Filter 'plexus-classworlds-*.jar' | Select-Object -First 1
if (-not $classworldsJar) {
  throw "Could not find plexus-classworlds jar under $mavenHome\boot"
}

$m2confPath = Join-Path $mavenHome 'bin\m2.conf'
if (-not (Test-Path $m2confPath)) {
  throw "Could not find classworlds config: $m2confPath"
}

$enableNativeAccess = ($javaMajor -ge 17)

$jvmConfigArgs = @()
$jvmConfigPath = Join-Path $projectBaseDir '.mvn\jvm.config'
if (Test-Path $jvmConfigPath) {
  foreach ($line in Get-Content $jvmConfigPath) {
    $trimmed = $line.Trim()
    if (-not $trimmed -or $trimmed.StartsWith('#')) { continue }
    $jvmConfigArgs += ($trimmed -split '\s+')
  }
}

$javaArgs = @()
$javaArgs += $jvmConfigArgs
if ($enableNativeAccess) { $javaArgs += '--enable-native-access=ALL-UNNAMED' }

if ($env:MAVEN_OPTS) {
  $javaArgs += ($env:MAVEN_OPTS -split '\s+')
}

$javaArgs += @(
  '-classpath', $classworldsJar.FullName,
  "-Dclassworlds.conf=$m2confPath",
  "-Dmaven.home=$mavenHome",
  "-Dlibrary.jansi.path=$($mavenHome)\lib\jansi-native",
  "-Dmaven.multiModuleProjectDirectory=$projectBaseDir",
  'org.codehaus.plexus.classworlds.launcher.Launcher'
)

if ($env:MAVEN_ARGS) {
  $javaArgs += ($env:MAVEN_ARGS -split '\s+')
}

$javaArgs += $Args

# Always run Maven from the project base directory so callers can invoke
# this script from anywhere (e.g., workspace root tasks).
Push-Location $projectBaseDir
try {
  & $javaCmd @javaArgs
  exit $LASTEXITCODE
} finally {
  Pop-Location
}
