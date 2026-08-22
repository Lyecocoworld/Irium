@echo off
rem IriumWatcher : relance le watcher d'attach au logon (survit aux reboots PC).
rem Sans fenetre : bash tourne en arriere-plan, log dans harness/watch.log.
"C:\Program Files\Git\bin\bash.exe" -l -c "/c/Users/space/Code/Irium/harness/watch-attach.sh >> /c/Users/space/Code/Irium/harness/watch.log 2>&1" &
