param([int]$HttpsPort=8443)
$ErrorActionPreference='Stop'
$identity=[Security.Principal.WindowsIdentity]::GetCurrent()
$principal=[Security.Principal.WindowsPrincipal]::new($identity)
if(-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)){
 $process=Start-Process powershell.exe -Verb RunAs -WindowStyle Hidden -Wait -PassThru -ArgumentList @('-NoProfile','-File',('"'+$PSCommandPath+'"'),'-HttpsPort',[string]$HttpsPort)
 if($process.ExitCode -ne 0){throw 'Administrator firewall configuration did not complete'}
 return
}
$rule=Get-NetFirewallRule -DisplayName 'Farm Management LAN HTTPS' -ErrorAction SilentlyContinue
if(-not $rule){New-NetFirewallRule -DisplayName 'Farm Management LAN HTTPS' -Direction Inbound -Action Allow -Protocol TCP -LocalPort $HttpsPort -Profile Private -RemoteAddress LocalSubnet -ErrorAction Stop | Out-Null}
Write-Host "HTTPS port $HttpsPort allowed for the Private local subnet. PostgreSQL remains private."
