[CmdletBinding()]
param(
	[switch]$Build
)

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$api = Join-Path $root 'mealpilot-api'
$web = Join-Path $root 'mealpilot-web'

Write-Host "Starting MealPilot dev environment"
Write-Host "- Backend:  http://localhost:9000"
Write-Host "- Frontend: http://localhost:5173"
Write-Host ""

# Start backend in a separate PowerShell window
$backendArgs = @(
	'-NoProfile',
	'-ExecutionPolicy', 'Bypass',
	'-NoExit',
	'-Command',
	"Set-Location \"$api\"; .\\run-dev.ps1" + ($(if ($Build) { ' -Build' } else { '' })) + ""
)

Start-Process -FilePath 'powershell' -WorkingDirectory $api -ArgumentList $backendArgs | Out-Null

# Start frontend in a separate PowerShell window
$frontendArgs = @(
	'-NoProfile',
	'-ExecutionPolicy', 'Bypass',
	'-NoExit',
	'-Command',
	"Set-Location \"$web\"; npm install; npm run dev"
)

Start-Process -FilePath 'powershell' -WorkingDirectory $web -ArgumentList $frontendArgs | Out-Null

Write-Host "Opened two terminals (backend + frontend)."
Write-Host "Open: http://localhost:5173"
