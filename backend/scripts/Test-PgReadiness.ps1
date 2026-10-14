param([switch]$Strict)

# Read public source only. Never read .env, process credentials, or call a payment/network endpoint.
$ErrorActionPreference = 'Stop'
$taskRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$taskChecks = [Collections.Generic.List[object]]::new()
function Add-PgCheck([string]$Id, [string]$State, [string]$Owner, [string]$Action) {
    $taskChecks.Add([pscustomobject]@{ id=$Id; state=$State; owner=$Owner; action=$Action })
}

$taskBusiness = [IO.File]::ReadAllText((Join-Path $taskRoot 'frontend/src/lib/business.ts'))
foreach ($taskField in @('companyName','representative','registrationNumber','address','phone','email','mailOrderNumber','hostingProvider')) {
    $taskMatch = [regex]::Match($taskBusiness, '(?m)^\s*' + $taskField + '\s*:\s*"([^"\r\n]+)"\s*,?\s*$')
    Add-PgCheck ('BUSINESS_' + $taskField) $(if ($taskMatch.Success) {'SOURCE_PRESENT'} else {'BLOCKED'}) 'PD/representative/FE' 'Confirm the public business value and its agreement with submitted PG documents.'
}

$taskPolicy = [IO.File]::ReadAllText((Join-Path $taskRoot 'frontend/src/components/PolicyPage.tsx'))
$taskEmptyPolicy = [regex]::IsMatch($taskPolicy, '<article\b[^>]*data-slot="policy-body"[^>]*/>', [Text.RegularExpressions.RegexOptions]::Singleline)
Add-PgCheck 'POLICY_BODY' $(if ($taskEmptyPolicy) {'BLOCKED'} else {'MANUAL_REQUIRED'}) 'PD/FE' 'Publish approved terms/privacy/refund v3; source presence alone does not confirm rendered content.'
Add-PgCheck 'POLICY_DIRECT_SHARING' 'MANUAL_REQUIRED' 'PD' 'Remove first-release phone/Alimtalk collection and align gift delivery, first provision, cancellation and retention.'

$taskContract = Get-Content -Raw -LiteralPath (Join-Path $taskRoot 'docs/openapi/api-v1.json') | ConvertFrom-Json
foreach ($taskEntry in @(
    @{id='PRODUCT_API';path='/api/v1/products';method='get'},
    @{id='CREATE_TOPUP_API';path='/api/v1/top-up-orders';method='post'},
    @{id='CONFIRM_TOPUP_API';path='/api/v1/top-up-orders/{orderId}/confirm';method='post'},
    @{id='LOOKUP_TOPUP_API';path='/api/v1/top-up-orders/{orderId}';method='get'})) {
    $taskOperation = $taskContract.paths.PSObject.Properties[$taskEntry.path]
    $taskExists = $null -ne $taskOperation -and $null -ne $taskOperation.Value.PSObject.Properties[$taskEntry.method]
    Add-PgCheck $taskEntry.id $(if ($taskExists) {'SOURCE_PRESENT'} else {'BLOCKED'}) 'BE-A' 'Verify this contract on the fixed staging URL; contract presence is not live E2E evidence.'
}

Add-PgCheck 'FIXED_STAGING_ORIGIN' 'MANUAL_REQUIRED' 'BE-A/BE-B/FE' 'Confirm HTTPS frontend/backend proxy, Kakao callback, session cookie and CSRF on real browsers.'
Add-PgCheck 'TEST_PAYMENT_E2E' 'MANUAL_REQUIRED' 'BE-A/FE' 'After secure local test-key configuration, verify auth/approval/one grant/order lookup/refund; never share secret keys.'
Add-PgCheck 'PG_CHARGE_CLASSIFICATION' 'MANUAL_REQUIRED' 'representative/BE-A' 'Confirm shell top-up product classification, limits, refund and merchant-document requirements with Toss.'
Add-PgCheck 'PG_CONTRACT_AND_REVIEW' 'MANUAL_REQUIRED' 'representative' 'Confirm actual merchant contract and card review status; no source check proves PG approval.'
Add-PgCheck 'MIGRATION_UPGRADE' 'MANUAL_REQUIRED' 'BE-A' 'Review applied migration history and the PR33 earlier-version upgrade before deployment.'

$taskBuild = Join-Path $taskRoot 'backend/build'
[IO.Directory]::CreateDirectory($taskBuild) | Out-Null
$taskResult = [ordered]@{
    schemaVersion=1
    assessment='REPOSITORY_PREFLIGHT_ONLY'
    approvalConfirmed=$false
    checkedAtUtc=[DateTime]::UtcNow.ToString('o')
    blocked=@($taskChecks | Where-Object state -eq 'BLOCKED').Count
    manualRequired=@($taskChecks | Where-Object state -eq 'MANUAL_REQUIRED').Count
    checks=$taskChecks.ToArray()
}
$taskJson = $taskResult | ConvertTo-Json -Depth 6
[IO.File]::WriteAllText((Join-Path $taskBuild 'pg-readiness.json'), $taskJson, [Text.UTF8Encoding]::new($false))
$taskJson
if ($Strict -and ($taskResult.blocked -gt 0 -or $taskResult.manualRequired -gt 0)) { exit 2 }
