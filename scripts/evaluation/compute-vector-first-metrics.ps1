param(
    [string]$CsvPath = ".\docs\evaluation\vector-first-evaluation-dataset.csv"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $CsvPath)) {
    throw "CSV not found: $CsvPath"
}

$rows = Import-Csv -LiteralPath $CsvPath
$positiveLabels = @(
    "STRONG_MATCH",
    "GOOD_MATCH_WITH_GAPS",
    "TRANSFERABLE_OPPORTUNITY"
)

function Is-TrueValue {
    param([string]$Value)
    if ([string]::IsNullOrWhiteSpace($Value)) {
        return $false
    }
    $normalized = $Value.Trim().ToLowerInvariant()
    return $normalized -in @("true", "1", "yes", "si")
}

function Has-Label {
    param($Row)
    return -not [string]::IsNullOrWhiteSpace($Row.human_label)
}

function Get-PrecisionAtK {
    param(
        [array]$ProfileRows,
        [int]$K
    )

    $topRows = @($ProfileRows | Sort-Object {[int]$_.analysis_rank} | Select-Object -First $K)
    if ($topRows.Count -lt $K) {
        return "N/A (expected $K rows)"
    }
    $unlabeled = @($topRows | Where-Object { -not (Has-Label $_) })
    if ($unlabeled.Count -gt 0) {
        return "N/A (missing labels)"
    }

    $positive = @($topRows | Where-Object { $positiveLabels -contains $_.human_label.Trim() }).Count
    return "{0:N3} ({1}/{2})" -f ($positive / $K), $positive, $K
}

$labeledRows = @($rows | Where-Object { Has-Label $_ })
$falsePositiveCount = @($rows | Where-Object { Is-TrueValue $_.is_false_positive }).Count
$unclearCount = @($rows | Where-Object { $_.human_label -eq "UNCLEAR" }).Count
$needsReviewCount = @($rows | Where-Object { Is-TrueValue $_.needs_review }).Count

Write-Host "Vector-first evaluation metrics"
Write-Host "CSV: $CsvPath"
Write-Host "Rows: $($rows.Count)"
Write-Host "Labeled rows: $($labeledRows.Count)"
Write-Host "False positives: $falsePositiveCount"
Write-Host "UNCLEAR: $unclearCount"
Write-Host "Needs review: $needsReviewCount"
Write-Host ""

Write-Host "Labels by profile"
$profiles = $rows | Group-Object profile_id | Sort-Object Name
foreach ($profile in $profiles) {
    $profileRows = @($profile.Group)
    $profileName = ($profileRows | Select-Object -First 1).profile_name
    if ([string]::IsNullOrWhiteSpace($profileName)) {
        $profileName = "(missing profile_name)"
    }

    Write-Host "Profile $($profile.Name): $profileName"
    Write-Host "  Rows: $($profileRows.Count)"
    Write-Host "  Labeled: $(@($profileRows | Where-Object { Has-Label $_ }).Count)"

    $labelGroups = $profileRows |
        Where-Object { Has-Label $_ } |
        Group-Object human_label |
        Sort-Object Name

    if ($labelGroups.Count -eq 0) {
        Write-Host "  Labels: none"
    } else {
        foreach ($labelGroup in $labelGroups) {
            Write-Host "  $($labelGroup.Name): $($labelGroup.Count)"
        }
    }

    Write-Host "  Precision@5: $(Get-PrecisionAtK -ProfileRows $profileRows -K 5)"
    Write-Host "  Precision@10: $(Get-PrecisionAtK -ProfileRows $profileRows -K 10)"
    Write-Host "  False positives: $(@($profileRows | Where-Object { Is-TrueValue $_.is_false_positive }).Count)"
    Write-Host "  UNCLEAR: $(@($profileRows | Where-Object { $_.human_label -eq "UNCLEAR" }).Count)"
    Write-Host "  Needs review: $(@($profileRows | Where-Object { Is-TrueValue $_.needs_review }).Count)"
    Write-Host ""
}
