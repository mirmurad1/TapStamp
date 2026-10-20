<#
.SYNOPSIS
  Web push üçün VAPID açar cütü (P-256) yaradır və .env-ə əlavə ediləcək iki sətri çap edir.
  Açarı dəyişmək bütün abunələri etibarsız edir — bir dəfə yaradın.
.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\gen-vapid-keys.ps1
#>
$ErrorActionPreference = 'Stop'
function ConvertTo-Base64Url([byte[]]$bytes) { [Convert]::ToBase64String($bytes).Replace('+', '-').Replace('/', '_').TrimEnd('=') }

$ec = [System.Security.Cryptography.ECDsa]::Create([System.Security.Cryptography.ECCurve+NamedCurves]::nistP256)
$parameters = $ec.ExportParameters($true)
$point = New-Object byte[] 65
$point[0] = 4
[Array]::Copy($parameters.Q.X, 0, $point, 1, 32)
[Array]::Copy($parameters.Q.Y, 0, $point, 33, 32)

Write-Output "APP_PUSH_VAPID_PUBLIC_KEY=$(ConvertTo-Base64Url $point)"
Write-Output "APP_PUSH_VAPID_PRIVATE_KEY=$(ConvertTo-Base64Url $parameters.D)"
