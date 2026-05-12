param(
	[string]$Container = "datalaburo-postgres",
	[string]$Database = "datalaburo",
	[string]$User = "datalaburo"
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$backupDir = Join-Path $repoRoot "backups"
New-Item -ItemType Directory -Path $backupDir -Force | Out-Null

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$fileName = "datalaburo-$timestamp.sql"
$hostPath = Join-Path $backupDir $fileName
$containerPath = "/tmp/$fileName"

Write-Host "Creating PostgreSQL backup from container '$Container'..."
docker exec $Container sh -c "pg_dump -U $User -d $Database --format=plain --no-owner --no-privileges > $containerPath"

Write-Host "Copying backup to '$hostPath'..."
docker cp "${Container}:$containerPath" $hostPath

docker exec $Container rm -f $containerPath | Out-Null

Write-Host "Backup created: $hostPath"
