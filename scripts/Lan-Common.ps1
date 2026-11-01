function Read-LanSecret([string]$Root,[string]$Name){
 $value=(Get-Content -Raw -LiteralPath (Join-Path $Root ($Name+'.dpapi'))).Trim() | ConvertTo-SecureString
 return [Net.NetworkCredential]::new('',$value).Password
}
function Assert-LanLauncherMode {
 $service=Get-Service -Name 'FarmManagement-PostgreSQL' -ErrorAction SilentlyContinue
 if($service){throw 'This installation uses the PostgreSQL Windows service. Use Backup-Local.ps1 / Start-Production.ps1; the legacy LAN launcher cannot control this database.'}
}
function Stop-LanApplication($Config,[string]$Root){
 $task=Get-ScheduledTask -TaskName 'FarmManagement-LAN' -ErrorAction SilentlyContinue
 if($task -and $task.State -eq 'Running'){
  Stop-ScheduledTask -TaskName 'FarmManagement-LAN'
  for($attempt=0;$attempt -lt 60;$attempt++){
   if((Get-ScheduledTask -TaskName 'FarmManagement-LAN').State -ne 'Running'){break}
   Start-Sleep -Milliseconds 500
  }
  if((Get-ScheduledTask -TaskName 'FarmManagement-LAN').State -eq 'Running'){throw 'Application launcher has not stopped; backup was not started'}
 }
 $pidFile=Join-Path $Root 'app.pid'
 if(Test-Path -LiteralPath $pidFile){
  $appPid=[int](Get-Content -Raw -LiteralPath $pidFile)
  $process=Get-CimInstance Win32_Process -Filter "ProcessId=$appPid" -ErrorAction SilentlyContinue
  if($process -and $process.ExecutablePath -eq $Config.java -and $process.CommandLine.IndexOf($Config.jar,[StringComparison]::OrdinalIgnoreCase) -ge 0){Stop-Process -Id $appPid;Wait-Process -Id $appPid -Timeout 30 -ErrorAction SilentlyContinue}
 }
}
function Start-LanApplication([string]$Root){
 $task=Get-ScheduledTask -TaskName 'FarmManagement-LAN' -ErrorAction SilentlyContinue
 if($task){Start-ScheduledTask -TaskName 'FarmManagement-LAN'}
 else{Start-Process powershell.exe -ArgumentList @('-NoProfile','-WindowStyle','Hidden','-File',('"'+(Join-Path $Root 'Start-Lan.ps1')+'"')) -WindowStyle Hidden | Out-Null}
}
function Wait-LanHealth($Config,[string]$Root){
 $previousPreference=$ErrorActionPreference
 try{
  $ErrorActionPreference='Continue'
  for($attempt=0;$attempt -lt 60;$attempt++){
   $body=& curl.exe --silent --cacert (Join-Path $Root 'farm-lan-public.crt') --connect-timeout 1 --max-time 2 "https://localhost:$($Config.httpsPort)/actuator/health" 2>$null
   if($LASTEXITCODE -eq 0 -and $body -match '"status"\s*:\s*"UP"'){return $true}
   Start-Sleep -Milliseconds 500
  }
  return $false
 }finally{$ErrorActionPreference=$previousPreference}
}
