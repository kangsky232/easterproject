$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$environmentFiles = @(
    (Join-Path $projectRoot '.env.mqtt.local'),
    (Join-Path $projectRoot '.env.dingtalk.local')
)

foreach ($environmentFile in $environmentFiles) {
    if (-not (Test-Path -LiteralPath $environmentFile)) {
        continue
    }
    foreach ($rawLine in Get-Content -LiteralPath $environmentFile -Encoding utf8) {
        $line = $rawLine.Trim()
        if (-not $line -or $line.StartsWith('#') -or -not $line.Contains('=')) {
            continue
        }
        $parts = $line.Split('=', 2)
        $name = $parts[0].Trim()
        $value = $parts[1]
        if ($name) {
            [Environment]::SetEnvironmentVariable($name, $value, 'Process')
        }
    }
}

Push-Location (Join-Path $projectRoot 'backend')
try {
    $taskMavenRepository = Join-Path $env:TEMP 'smart-smoke-maven-repository'
    mvn "-Dmaven.repo.local=$taskMavenRepository" spring-boot:run
} finally {
    Pop-Location
}
