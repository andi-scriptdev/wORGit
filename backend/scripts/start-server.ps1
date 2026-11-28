# Starts the backend with the right settings so you do not retype them in every new terminal.
# First run: asks for the JDBC URL and database user (kept in .worgit-local.json, which git ignores) and
# makes a random JWT secret. Every run: asks for the database password (hidden; never saved).
# Run from the project folder:  powershell -ExecutionPolicy Bypass -File scripts\start-server.ps1
$root = Join-Path $PSScriptRoot ".."
$cfgPath = Join-Path $root ".worgit-local.json"

if (-not (Test-Path $cfgPath)) {
    Write-Host "First run: this is saved in .worgit-local.json (not the password)." -ForegroundColor Cyan
    $url  = Read-Host "JDBC URL, like jdbc:postgresql://HOST:5432/postgres?currentSchema=worgit"
    $user = Read-Host "Database user (session pooler: postgres.<project-ref>)"
    if (-not $url.StartsWith("jdbc:postgresql://")) {
        Write-Host "The URL must start with jdbc:postgresql:// (not postgresql://). Nothing saved." -ForegroundColor Red
        return
    }
    $bytes = New-Object byte[] 48
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    @{ url = $url.Trim(); user = $user.Trim(); jwtSecret = [Convert]::ToBase64String($bytes) } |
        ConvertTo-Json | Set-Content -Path $cfgPath -Encoding UTF8
}

$cfg = Get-Content -Path $cfgPath -Raw | ConvertFrom-Json
if (-not $cfg.url.StartsWith("jdbc:postgresql://")) {
    Write-Host "Saved URL does not start with jdbc:postgresql://. Delete .worgit-local.json and run again." -ForegroundColor Red
    return
}

$secure = Read-Host "Database password" -AsSecureString
$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try { $env:WORGIT_DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr) }
finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }

$env:WORGIT_DB_URL = $cfg.url
$env:WORGIT_DB_USER = $cfg.user
$env:WORGIT_JWT_SECRET = $cfg.jwtSecret

Set-Location $root
mvn spring-boot:run
