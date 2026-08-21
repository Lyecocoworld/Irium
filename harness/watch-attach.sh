#!/bin/bash
# Watcher persistant : attache l'agent Irium à TOUT process java/javaw chargant
# 26.2.jar. Suit TOUS les PIDs (pas juste le dernier) — un attach par process,
# jamais deux fois le même. Les PIDs disparus sont oubliés (Windows peut
# réutiliser un PID pour un NOUVEAU client, qui doit alors être attaché).
# Le jar est résolu à CHAQUE boucle (jar le plus récent d'irium-agent-*.jar)
# pour survivre aux mvn clean qui suppriment la version courante.
ATTACH_CP="C:/Users/space/Code/Irium/harness/.lab"
JAVA="/c/Program Files/Eclipse Adoptium/jdk-25.0.3.9-hotspot/bin/java"
declare -A DONE   # pid -> 1 (déjà attaché)

while true; do
  JAR=$(ls -t /c/Users/space/Code/Irium/agent/target/irium-agent-*.jar 2>/dev/null | head -1)
  if [ -n "$JAR" ]; then
    JAR=$(cygpath -w "$JAR")   # la JVM Windows ne comprend pas /c/Users/...
  else
    sleep 2
    continue
  fi
  PIDS=$(powershell.exe -NoProfile -Command "Get-CimInstance Win32_Process | Where-Object { (\$_.Name -eq 'javaw.exe' -or \$_.Name -eq 'java.exe') -and \$_.CommandLine -match '26.2' -and \$_.CommandLine -notmatch 'server.jar' } | Select-Object -ExpandProperty ProcessId" 2>/dev/null | tr -d '\r')
  # oublier les PIDs disparus (réutilisation de PID par un nouveau process)
  for K in "${!DONE[@]}"; do
    grep -qx "$K" <<< "$PIDS" || unset "DONE[$K]"
  done
  while read -r PID; do
    [ -z "$PID" ] && continue
    if [ -z "${DONE[$PID]}" ]; then
      echo "[watch $(date +%H:%M:%S)] nouveau client : PID $PID — attach $(basename $JAR)..."
      # log seul : DONE marqué quoi qu'il arrive (pas de retry en boucle sur un PID mort)
      "$JAVA" -cp "$ATTACH_CP" Attach "$PID" "$JAR" 2>&1 | head -2
      DONE[$PID]=1
    fi
  done <<< "$PIDS"
  sleep 2
done
