# Installe la tâche planifiée IriumWatcher (logon, survit aux reboots PC).
# Méthode native Register-ScheduledTask (pas de parsing schtasks à-la-morse).
$action = New-ScheduledTaskAction -Execute 'C:\Program Files\Git\bin\bash.exe' `
    -Argument '-l -c "/c/Users/space/Code/Irium/harness/watch-attach.sh >> /c/Users/space/Code/Irium/harness/watch.log 2>&1"'
$trigger = New-ScheduledTaskTrigger -AtLogOn
$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -ExecutionTimeLimit ([TimeSpan]::Zero) -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 1)
Register-ScheduledTask -TaskName 'IriumWatcher' -Action $action -Trigger $trigger -Settings $settings -Force | Out-Null
Write-Host "IriumWatcher installée."
Get-ScheduledTask -TaskName 'IriumWatcher' | Format-List TaskName, State
