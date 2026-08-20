#!/usr/bin/env bash
# Surveille l'apparition d'un NOUVEAU javaw avec 26.2.jar, puis attache l'agent.
set -u
JH="/c/Program Files/Eclipse Adoptium/jdk-25.0.3.9-hotspot/bin"
AGENT="C:/Users/space/Code/Irium/agent/target/irium-agent-0.4.0.jar"
LOG="/c/Users/space/AppData/Roaming/.sklauncher/instances/26-2/logs/latest.log"
OLD_PID=18792

echo "[watch] en attente du redémarrage du client 26.2 (ancien PID $OLD_PID)..."
NEWPID=""
for i in $(seq 1 600); do
  # cherche les javaw dont la ligne de commande contient 26.2.jar
  for PID in $(powershell.exe -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='javaw.exe'\" | Select-Object -ExpandProperty ProcessId" 2>/dev/null | tr -d '\r'); do
    [ "$PID" = "$OLD_PID" ] && continue
    CMD=$(powershell.exe -NoProfile -Command "(Get-CimInstance Win32_Process -Filter \"ProcessId=$PID\").CommandLine" 2>/dev/null | tr -d '\r')
    if echo "$CMD" | grep -q "26.2.jar"; then NEWPID=$PID; break 2; fi
  done
  sleep 2
done
[ -z "$NEWPID" ] && { echo "[watch] TIMEOUT : aucun nouveau client détecté"; exit 1; }
echo "[watch] nouveau client détecté : PID $NEWPID"

# attendre que le jeu soit assez lancé (fenêtre = logs écrits / classe Minecraft chargée)
sleep 8
echo "[watch] attach de l'agent..."
"$JH/java" -cp "C:/Users/space/Code/Irium/harness/.lab;C:/Program Files/Eclipse Adoptium/jdk-25.0.3.9-hotspot/lib/jrt-fs.jar" Attach "$NEWPID" "$AGENT" || exit 1

# vérifier le bootstrap dans le log client (rotation éventuelle)
for i in $(seq 1 30); do
  if grep -aq "irium-agent.*bootstrapping" "$LOG" 2>/dev/null && [ "$LOG" -nt /tmp/.irium-watch-marker 2>/dev/null -o ! -f /tmp/.irium-watch-marker ]; then
    if tail -c 200000 "$LOG" | grep -aq "$(date +%H):.*irium-agent.*bootstrapping\|bootstrapping (force)"; then break; fi
  fi
  sleep 2
done
touch /tmp/.irium-watch-marker
tail -c 100000 "$LOG" | grep -a "irium" | tail -5
echo "[watch] PRÊT — connecte-toi à 127.0.0.1:25599"
