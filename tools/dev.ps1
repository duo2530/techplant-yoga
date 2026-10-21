param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('Start', 'Stop', 'Run')]
    [string]$Action,
    [ValidateSet('backend', 'pc', 'h5')]
    [string]$Service
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$runtime = Join-Path $root '.tmp/dev'
$scriptPath = $PSCommandPath
$services = @(
    @{ Name = 'backend'; Directory = 'backend'; Port = 8080; Url = 'http://localhost:8080' },
    @{ Name = 'pc'; Directory = 'frontend/pc'; Port = 80; Url = 'http://localhost' },
    @{ Name = 'h5'; Directory = 'frontend/uni-app'; Port = 9090; Url = 'http://localhost:9090' }
)

function Get-ServiceProcess($item) {
    $statePath = Join-Path $runtime "$($item.Name).json"
    if (!(Test-Path -LiteralPath $statePath)) { return $null }
    $state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
    $process = Get-Process -Id $state.ProcessId -ErrorAction SilentlyContinue
    if ($null -eq $process) { return $null }
    if ($process.StartTime.ToUniversalTime().Ticks.ToString() -ne $state.StartTicks) { return $null }
    $details = Get-CimInstance Win32_Process -Filter "ProcessId = $($process.Id)"
    if ($null -eq $details -or !$details.CommandLine.Contains($scriptPath)) {
        throw "Cannot verify process ownership for $($item.Name). State: $statePath"
    }
    return $process
}

function Stop-ServiceProcess($item) {
    $process = Get-ServiceProcess $item
    if ($null -ne $process) {
        & taskkill.exe /PID $process.Id /T /F | Out-Host
        if ($LASTEXITCODE -ne 0 -and (Get-Process -Id $process.Id -ErrorAction SilentlyContinue)) {
            throw "Failed to stop $($item.Name)."
        }
        $process.WaitForExit()
        Write-Host "Stopped $($item.Name)."
    } else {
        Write-Host "$($item.Name) is not running under this script."
    }
    $statePath = Join-Path $runtime "$($item.Name).json"
    if (Test-Path -LiteralPath $statePath) { Remove-Item -LiteralPath $statePath }
}

function Test-ServicePort($port) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $connection = $client.ConnectAsync('127.0.0.1', $port)
        return ($connection.Wait(300) -and $client.Connected)
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

try {
    if ($Action -eq 'Run') {
        if (!$Service) { throw 'Run requires -Service.' }
        $item = $services | Where-Object { $_.Name -eq $Service }
        Set-Location (Join-Path $root $item.Directory)
        if ($Service -eq 'backend') {
            & java.exe -jar (Join-Path $root 'backend/ruoyi-admin/target/ruoyi-admin.jar') --server.port=8080
        } elseif ($Service -eq 'pc') {
            & npm.cmd run dev -- --port 80 --strictPort
        } else {
            & npm.cmd run dev:h5 -- --port 9090 --strictPort
        }
        exit $LASTEXITCODE
    }

    New-Item -ItemType Directory -Path $runtime -Force | Out-Null
    $lock = $null
    try {
        $lock = [System.IO.File]::Open((Join-Path $runtime 'control.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
    } catch {
        throw 'Another start/stop command is running. Wait for it to finish.'
    }

    if ($Action -eq 'Stop') {
        $failed = $false
        foreach ($item in @($services[2], $services[1], $services[0])) {
            try { Stop-ServiceProcess $item } catch { Write-Warning $_; $failed = $true }
        }
        if ($failed) { throw 'Some services could not be stopped. See messages above.' }
        exit 0
    }

    foreach ($command in @('java.exe', 'mvn.cmd', 'node.exe', 'npm.cmd')) {
        if (!(Get-Command $command -ErrorAction SilentlyContinue)) { throw "Missing command: $command" }
    }
    $pending = @()
    foreach ($item in $services) {
        if ($null -ne (Get-ServiceProcess $item)) {
            Write-Host "$($item.Name) is already running."
            continue
        }
        if (Test-ServicePort $item.Port) {
            throw "Port $($item.Port) is already in use. Stop its owner before starting $($item.Name)."
        }
        $pending += $item
    }

    if (@($pending | Where-Object { $_.Name -eq 'backend' }).Count -gt 0) {
        Push-Location (Join-Path $root 'backend')
        try {
            & mvn.cmd '-DskipTests' package
            if ($LASTEXITCODE -ne 0) { throw 'Backend build failed.' }
        } finally { Pop-Location }
    }
    foreach ($item in @($pending | Where-Object { $_.Name -ne 'backend' })) {
        $directory = Join-Path $root $item.Directory
        if (!(Test-Path -LiteralPath (Join-Path $directory 'node_modules'))) {
            Push-Location $directory
            try {
                & npm.cmd ci
                if ($LASTEXITCODE -ne 0) { throw "Dependency installation failed: $($item.Name)" }
            } finally { Pop-Location }
        }
    }

    $started = @()
    try {
        foreach ($item in $pending) {
            $log = Join-Path $runtime "$($item.Name).log"
            $errorLog = Join-Path $runtime "$($item.Name).error.log"
            $process = Start-Process -FilePath "$PSHOME/powershell.exe" -WindowStyle Hidden -PassThru `
                -ArgumentList @('-NoLogo', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', "`"$scriptPath`"", '-Action', 'Run', '-Service', $item.Name) `
                -WorkingDirectory $root -RedirectStandardOutput $log -RedirectStandardError $errorLog
            try {
                @{
                    ProcessId = $process.Id
                    StartTicks = $process.StartTime.ToUniversalTime().Ticks.ToString()
                } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runtime "$($item.Name).json") -Encoding UTF8
            } catch {
                & taskkill.exe /PID $process.Id /T /F | Out-Host
                throw
            }
            $started += $item
            $deadline = (Get-Date).AddSeconds(180)
            while (!(Test-ServicePort $item.Port)) {
                if ($process.HasExited) { throw "$($item.Name) exited. Logs: $log and $errorLog" }
                if ((Get-Date) -gt $deadline) { throw "$($item.Name) timed out. Logs: $log and $errorLog" }
                Start-Sleep -Milliseconds 500
            }
            if ($process.HasExited) { throw "$($item.Name) exited. Logs: $log and $errorLog" }
            Write-Host "Started $($item.Name): $($item.Url)"
        }
    } catch {
        foreach ($item in $started) {
            try { Stop-ServiceProcess $item } catch { Write-Warning $_ }
        }
        throw
    }
    Write-Host "Services are running. Logs: $runtime"
    Write-Host 'Use stop.bat to stop them.'
} catch {
    Write-Host "ERROR: $_" -ForegroundColor Red
    exit 1
} finally {
    if ($null -ne $lock) { $lock.Dispose() }
}
