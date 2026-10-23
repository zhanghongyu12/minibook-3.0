<#
.SYNOPSIS
  Pre-commit guard: make sure no remote server address, password or key file gets committed.

.DESCRIPTION
  Default: check only what is staged (git diff --cached). With -All: every tracked file plus
  untracked files (gitignored ones excluded). Any hit prints "file:line:reason: snippet" and
  exits 1; a clean run exits 0.

  Where the real values live instead of the repo:
    * App endpoint address   -> <repo>/sync.properties      (gitignored; template sync.properties.example)
    * Server config/secrets  -> server/.env (local) or the server's /opt/docker/minibook-api/.env
    * test account / keystore / phone backups -> test-account.txt, keystore.properties, *.jks,
      minibook_backup_*.json

  Rule notes (kept deliberately narrow so the guard stays trustworthy):
    * Public IPv4 only counts when it looks like an endpoint: preceded by "@" or "//", followed by
      ":port", or on a config-looking line (KEY=... / KEY: ...). User-Agent strings and SVG path
      data are skipped. Private/loopback/link-local/TEST-NET/0.0.0.0 never count.
    * Credential rules skip test sources (tests/, androidTest/) which use throwaway fixtures.
    * Files deleted from disk are not reported as "forbidden file".

  NOTE: keep this file ASCII-only on purpose. Windows PowerShell 5.1 reads BOM-less files as ANSI,
  so any non-ASCII text here would break the parser (verified 2026-10-23). The Chinese explanation
  lives in docs/14_cloud_sync.md section 17.

.EXAMPLE
  powershell -NoProfile -File tools/check-secrets.ps1
  powershell -NoProfile -File tools/check-secrets.ps1 -All
#>
param([switch]$All)

$ErrorActionPreference = 'Stop'
$repo = (& git rev-parse --show-toplevel).Trim()
Set-Location $repo

# ---- files that must never be committed ------------------------------------------------
$forbiddenExact = @('sync.properties', 'keystore.properties', 'local.properties', 'test-account.txt', 'cred.txt')
$forbiddenRegex = @(
    '\.jks$', '\.keystore$', '\.env$', '\.env\.(?!.*\.example$).+$',
    'minibook_backup_.*\.json$', 'minibook-migrate/', '\.claude-runs/'
)

# ---- content rules --------------------------------------------------------------------
$publicIpRegex = [regex]'(?<![\d.])(?!(?:10|127|0)\.)(?!(?:192\.168)\.)(?!(?:172\.(?:1[6-9]|2\d|3[01]))\.)(?!(?:169\.254)\.)(?!(?:192\.0\.2|198\.51\.100|203\.0\.113)\.)(?:(?:25[0-5]|2[0-4]\d|1?\d?\d)\.){3}(?:25[0-5]|2[0-4]\d|1?\d?\d)(?![\d.])'
$uaMarker = 'Mozilla/|AppleWebKit|Gecko/'
$assignLine = '^\s*(?:export\s+)?[A-Za-z_][\w.\-]*\s*[=:]'
$rules = @(
    @{ name = 'public IPv4 endpoint (remote host must stay out of the repo)'; kind = 'ip' }
    @{ name = 'private key block';                     regex = 'BEGIN [A-Z ]*PRIVATE KEY';                 testExempt = $false }
    @{ name = 'DSN carrying a real password';          regex = '(?i)(?:mysql|mariadb|postgres(?:ql)?)\.?\w*://[^:\s/]+:(?!CHANGE_ME|<|\$\{)[^@\s]{6,}@'; testExempt = $true }
    @{ name = 'JWT_SECRET carrying a real value';      regex = '(?i)JWT_SECRET\s*[=:]\s*["'']?(?!dev-insecure|CHANGE_ME|<|\$\{|")[A-Za-z0-9_\-\.]{24,}'; testExempt = $true }
    @{ name = 'probable password/secret assignment';   regex = '(?i)(?:password|passwd|pwd|api[_-]?key|access[_-]?key|secret[_-]?key)\s*[=:]\s*["''][^"'']{12,}["'']'; testExempt = $true }
)

function Get-Targets {
    if ($All) {
        @((& git ls-files) + (& git ls-files --others --exclude-standard)) | Sort-Object -Unique
    } else {
        @(& git diff --cached --name-only --diff-filter=ACMR)
    }
}

$targets = @(Get-Targets | Where-Object { $_ })
if ($targets.Count -eq 0) {
    Write-Host 'Nothing staged - nothing to check (use -All for a full scan).' -ForegroundColor Yellow
    exit 0
}

$hits = New-Object System.Collections.Generic.List[string]
foreach ($f in $targets) {
    $exists = Test-Path -LiteralPath $f -PathType Leaf
    if ($exists) {
        if ($forbiddenExact -contains $f) { $hits.Add(("{0}(0)`tforbidden file (exact name)" -f $f)) }
        foreach ($re in $forbiddenRegex) {
            if ($f -match $re) { $hits.Add(("{0}(0)`tforbidden file (matched /{1}/)" -f $f, $re)); break }
        }
    } elseif ($forbiddenExact -contains $f) {
        continue   # staged deletion of a secret file is good news, not a finding
    }
    if (-not $exists) { continue }
    try { $bytes = [System.IO.File]::ReadAllBytes($f) } catch { continue }
    if ($bytes -contains 0) { continue }   # skip binary
    $isTestPath = $f -match '(?i)(^|/)(tests?|androidTest)(/|$)'
    $lines = @(Get-Content -LiteralPath $f -Encoding UTF8 -ErrorAction SilentlyContinue)
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]
        foreach ($r in $rules) {
            if ($r.kind -eq 'ip') {
                if ($line -match $uaMarker) { continue }
                foreach ($m in $publicIpRegex.Matches($line)) {
                    $before = $line.Substring(0, $m.Index)
                    $after = $line.Substring($m.Index + $m.Length)
                    if ($before -match '(@|//)$' -or $after -match '^:\d{1,5}(\D|$)' -or $line -match $assignLine) {
                        $hits.Add(("{0}({1})`t{2}: {3}" -f $f, ($i + 1), $r.name, $line.Trim()))
                        break
                    }
                }
                continue
            }
            if ($r.testExempt -and $isTestPath) { continue }
            if ($line -match $r.regex) { $hits.Add(("{0}({1})`t{2}: {3}" -f $f, ($i + 1), $r.name, $line.Trim())) }
        }
    }
}

$scope = if ($All) { 'all tracked + untracked' } else { 'staged' }
Write-Host ("Checked {0} file(s) [{1}]." -f $targets.Count, $scope)
if ($hits.Count -eq 0) {
    Write-Host 'OK  no remote address / password / key / forbidden file found.' -ForegroundColor Green
    exit 0
}
Write-Host ("FAIL  {0} hit(s) - do NOT commit. Move the real values out of the repo:" -f $hits.Count) -ForegroundColor Red
$hits | Sort-Object -Unique | ForEach-Object { Write-Host ("   " + $_) -ForegroundColor Red }
Write-Host '   where they belong: sync.properties (app endpoint) / server/.env or the server .env (server config) / files kept on the server (ops details)'
exit 1
