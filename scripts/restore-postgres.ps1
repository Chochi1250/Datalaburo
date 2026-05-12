param(
	[Parameter(Mandatory = $true)]
	[string]$File,
	[string]$Container = "datalaburo-postgres",
	[string]$Database = "datalaburo",
	[string]$User = "datalaburo"
)

$ErrorActionPreference = "Stop"

$resolvedFile = (Resolve-Path -LiteralPath $File -ErrorAction Stop).Path
if (-not (Test-Path -LiteralPath $resolvedFile -PathType Leaf)) {
	throw "Backup file not found: $File"
}

$containerPath = "/tmp/datalaburo-restore.sql"

Write-Warning "Restore is safest on a clean database. Existing data may cause conflicts if rows or schema objects already exist."
Write-Host "Copying '$resolvedFile' to container '$Container'..."
docker cp $resolvedFile "${Container}:$containerPath"

Write-Host "Restoring PostgreSQL backup into database '$Database'..."
docker exec $Container psql -U $User -d $Database -v ON_ERROR_STOP=1 -f $containerPath

docker exec $Container rm -f $containerPath | Out-Null

Write-Host "Restore finished."
