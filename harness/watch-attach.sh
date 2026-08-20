#!/bin/bash
# Watcher persistant : attend TOUT nouveau process javaw chargant 26.2.jar
# et y attache l'agent Irium. Boucle à l'infini — un attach par nouveau process.
JAR="C:/Users/space/Code/Irium/agent/target/irium-agent-0.4.2.jar"
ATTACH_CP="C:/Users/space/Code/Irium/harness/.lab"
JAVA="/c/Program Files/Eclipse Adoptium/jdk-25.0.3.9-hotspot/bin/java"
LAST=""

while true; do
  PID=$(powershell.exe -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='javaw.exe'\" | Where-Object { \$_.CommandLine -match '26.2' } | Select-Object -ExpandProperty ProcessId" 2>/dev/null | tr -d '\r' | head -1)
  if [ -n "$PID" ] && [ "$PID" != "$LAST" ]; then
    echo "[watch $(date +%H:%M:%S)] nouveau client détecté : PID $PID — attach..."
    "$JAVA" -cp "$ATTACH_CP" Attach "$PID" "$JAR" 2>&1 | head -2
    LAST="$PID"
  fi
  sleep 2
done
