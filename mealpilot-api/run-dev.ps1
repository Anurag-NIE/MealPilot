[CmdletBinding()]
param(
	[int]$PreferredPort = 9000,
	[switch]$AutoPort,
	[switch]$Build
)

$ErrorActionPreference = 'Stop'

$apiRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$jar = Join-Path $apiRoot 'target\mealpilot-api-0.0.1-SNAPSHOT.jar'

function Test-PortFree([int]$Port) {
	try {
		$listen = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
		return -not [bool]$listen
	} catch {
		# If Get-NetTCPConnection isn't available for some reason, fall back to netstat.
		$line = (netstat -ano -p tcp | findstr (":" + $Port) | findstr "LISTENING")
		return -not [bool]$line
	}
}

if ($Build -or -not (Test-Path $jar)) {
	Write-Host "Building app jar..."
	& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $apiRoot 'mvnw.ps1') -q -DskipTests package
}

if (-not (Test-Path $jar)) {
	throw "Jar not found: $jar"
}

$port = $PreferredPort
if ($AutoPort) {
	$candidates = @($PreferredPort, 9000, 9001, 8000, 8081, 8090)
	$port = $null
	foreach ($p in $candidates) {
		if (Test-PortFree -Port $p) {
			$port = $p
			break
		}
		Write-Host "Port $p is in use; trying next..."
	}
	if (-not $port) {
		throw "No free port found in candidate list: $($candidates -join ', ')"
	}
} else {
	if (-not (Test-PortFree -Port $port)) {
		throw "Port $port is already in use. Re-run with -AutoPort or choose another -PreferredPort."
	}
}

$url = "http://localhost:$port"
Write-Host "Starting MealPilot on $url"
Write-Host "(Jenkins may be on http://localhost:8080)"
Write-Host "MEALPILOT_URL=$url"

Set-Location $apiRoot
Write-Host "Using Maven wrapper runner (mvnw.ps1) to ensure Java 21+ runtime."

& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $apiRoot 'mvnw.ps1') spring-boot:run ("-Dspring-boot.run.arguments=--server.port=" + $port)
