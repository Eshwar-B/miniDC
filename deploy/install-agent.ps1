#Requires -RunAsAdministrator
<#
.SYNOPSIS
    MiniDC Windows Lender Node Installer and Pre-check Script.
#>

$ErrorActionPreference = "Stop"

Write-Host "=== MiniDC Windows Lender Agent Setup ===" -ForegroundColor Cyan

# 1. Check OS version
$os = Get-CimInstance Win32_OperatingSystem
Write-Host "Operating System: $($os.Caption) (Build $($os.BuildNumber))"
if ([System.Environment]::Is64BitOperatingSystem -ne $true) {
    Write-Error "MiniDC requires a 64-bit Windows operating system."
    exit 1
}

# 2. Check RAM
$ramGb = [Math]::Round($os.TotalVisibleMemorySize / 1024 / 1024, 1)
Write-Host "Total Memory: $ramGb GB"
if ($ramGb -lt 4.0) {
    Write-Error "MiniDC requires at least 4 GB of RAM (8 GB+ recommended)."
    exit 1
}

# 3. Check Free Disk Space
$systemDrive = (Get-CimInstance Win32_LogicalDisk -Filter "DeviceID='C:'")
$freeDiskGb = [Math]::Round($systemDrive.FreeSpace / 1024 / 1024 / 1024, 1)
Write-Host "Free Disk Space on C:: $freeDiskGb GB"
if ($freeDiskGb -lt 15.0) {
    Write-Warning "Less than 15 GB of free space available. May fail to store golden image and sandbox disks."
}

# 4. Check Virtualization Firmware Flag
$cpu = Get-CimInstance Win32_Processor
if ($cpu.VirtualizationFirmwareEnabled -ne $true) {
    Write-Error "CPU Virtualization is DISABLED in BIOS/UEFI. Please reboot into UEFI settings and enable Intel VT-x / AMD-V."
    exit 1
}
Write-Host "[OK] CPU hardware virtualization is enabled in BIOS/UEFI." -ForegroundColor Green

# 5. Check Hyper-V / Memory Integrity Conflict
$hypervisorActive = (Get-CimInstance Win32_ComputerSystem).HypervisorPresent
if ($hypervisorActive) {
    Write-Warning "Windows Hypervisor Platform / Core Isolation Memory Integrity is active. VirtualBox will run via the Hyper-V backend with reduced performance."
}

# 6. Verify or install VirtualBox
$vboxPath = "C:\Program Files\Oracle\VirtualBox\VBoxManage.exe"
if (-not (Test-Path $vboxPath)) {
    Write-Host "VirtualBox not detected. Downloading Oracle VirtualBox..." -ForegroundColor Yellow
    $vboxInstaller = "$env:TEMP\VirtualBox-Win.exe"
    $vboxUrl = "https://download.virtualbox.org/virtualbox/7.0.18/VirtualBox-7.0.18-162988-Win.exe"
    
    # TODO(phase-7): Pin exact SHA-256 hash for VirtualBox installer verification
    $expectedSha256 = "0000000000000000000000000000000000000000000000000000000000000000"

    Invoke-WebRequest -Uri $vboxUrl -OutFile $vboxInstaller
    $actualHash = (Get-FileHash -Path $vboxInstaller -Algorithm SHA256).Hash
    Write-Host "Downloaded VirtualBox installer SHA256: $actualHash"
    # if ($actualHash -ne $expectedSha256) { Write-Error "VirtualBox checksum mismatch!"; exit 1 }

    Write-Host "Silently installing VirtualBox..."
    Start-Process -FilePath $vboxInstaller -ArgumentList "--silent", "--ignore-reboot" -Wait
    Write-Host "[OK] VirtualBox installation finished." -ForegroundColor Green
} else {
    Write-Host "[OK] VirtualBox already installed at $vboxPath." -ForegroundColor Green
}

# 7. Create storage directories
$appData = "C:\ProgramData\MiniDC"
New-Item -ItemType Directory -Force -Path "$appData\images" | Out-Null
New-Item -ItemType Directory -Force -Path "$appData\leases" | Out-Null
Write-Host "[OK] Data directories created under $appData" -ForegroundColor Green

Write-Host "=== Setup completed successfully! Copy golden.vdi to $appData\images\golden.vdi ===" -ForegroundColor Cyan

