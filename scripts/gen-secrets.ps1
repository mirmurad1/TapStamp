<#
.SYNOPSIS
  .env.example əsasında .env faylı yaradır və boş sirləri kriptoqrafik təsadüfi dəyərlərlə doldurur.
.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\gen-secrets.ps1
  powershell -ExecutionPolicy Bypass -File scripts\gen-secrets.ps1 -Force   # mövcud .env-in üzərinə yazır
#>
param([switch]$Force)
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$example = Join-Path $root '.env.example'
$target = Join-Path $root '.env'

if ((Test-Path $target) -and -not $Force) {
    Write-Host '.env artıq mövcuddur. Üzərinə yazmaq üçün -Force istifadə edin (köhnə sirlər itəcək).'
    exit 1
}

$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
function New-RandomBytes([int]$count) {
    $bytes = New-Object byte[] $count
    $rng.GetBytes($bytes)
    return $bytes
}
function New-Key { [Convert]::ToBase64String((New-RandomBytes 32)) }
function New-Password { ([Convert]::ToBase64String((New-RandomBytes 24))).Replace('+', '-').Replace('/', '_').TrimEnd('=') }

$kekId = 'kek-' + (Get-Date -Format 'yyyy-MM')
$generated = @{
    'POSTGRES_PASSWORD'          = New-Password
    'DB_PLATFORM_OWNER_PASSWORD' = New-Password
    'DB_PLATFORM_APP_PASSWORD'   = New-Password
    'DB_PROVISIONER_PASSWORD'    = New-Password
    'APP_CRYPTO_KEKS'            = "${kekId}:$(New-Key)"
    'APP_CRYPTO_ACTIVE_KEK_ID'   = $kekId
    'APP_PHONE_HMAC_KEY'         = New-Key
    'APP_OTP_PEPPER'             = New-Key
    'APP_REDEMPTION_CODE_KEY'    = New-Key
    'APP_DEV_SIMULATOR_KEY'      = New-Key
    'APP_INTERNAL_TOKEN'         = New-Password
}

$lines = Get-Content -Path $example -Encoding UTF8 | ForEach-Object {
    if ($_ -match '^([A-Z0-9_]+)=$' -and $generated.ContainsKey($Matches[1])) {
        "$($Matches[1])=$($generated[$Matches[1]])"
    } else {
        $_
    }
}

# BOM-suz UTF-8 (docker compose BOM-u dəyişənin adına qatır)
[System.IO.File]::WriteAllLines($target, $lines, (New-Object System.Text.UTF8Encoding($false)))
Write-Host ".env yaradıldı: $target"
Write-Host 'Bu faylı heç kimlə paylaşmayın və git-ə əlavə etməyin.'
