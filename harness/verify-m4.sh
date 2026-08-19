#!/usr/bin/env bash
# ============================================================
# Irium M4 — harnais e2e canonique : le serveur STREAME du code
# compilé (module .irm) vers un client AGENT qui l'exécute.
#
# Prérequis :
#   - serveur Canvas 26.2 sur 127.0.0.1:25599 avec Irium-0.3.0.jar
#   - plugins/Irium/modules/hello.irm présent (compilé depuis
#     harness/HelloModule.java contre l'API agent)
# Sortie : PASS/FAIL. Exit 0 si tout vert.
# ============================================================
set -u
JAVA="/c/Program Files/Eclipse Adoptium/jdk-25.0.3.9-hotspot/bin/java"
JAVAC="/c/Program Files/Eclipse Adoptium/jdk-25.0.3.9-hotspot/bin/javac"
REPO="$(cd "$(dirname "$0")/.." && pwd)"
REPO_W=$(echo "$REPO" | sed 's|^/\([a-z]\)/|\U\1:/|')
AGENT_JAR="$REPO_W/agent/target/irium-agent-0.4.0.jar"
MODULE_SRC="$REPO/harness/HelloModule.java"
MODULE_OUT="/c/Users/space/irium-test/server/plugins/Irium/modules/hello.irm"
NETTY_CP_FILE="/c/Users/space/irium-test/client/netty.cp"
LOG="/c/Users/space/irium-test/server/logs/latest.log"
LAB_DIR="$REPO/harness/.lab"
LAB_CP="$REPO_W/harness/.lab"
PASS=0; FAIL=0
pass(){ echo "PASS: $1"; PASS=$((PASS+1)); }
fail(){ echo "FAIL: $1"; FAIL=$((FAIL+1)); }

# --- build agent + plugin ---
echo "=== [0] Builds canoniques ==="
for i in 1 2 3; do
  ( cd "$REPO/agent" && cmd.exe /c "C:\\Users\\space\\buildtools\\apache-maven-3.9.6\\bin\\mvn.cmd -q -B clean package" ) > "$REPO/harness/.mvn-agent.log" 2>&1
  J=""
  for w in 1 2 3 4 5 6 7 8 9 10; do
    if [ -s "$AGENT_JAR" ] && unzip -t "$AGENT_JAR" >/dev/null 2>&1; then J="ok"; break; fi
    sleep 1
  done
  [ -n "$J" ] && break
done
[ -n "$J" ] && pass "agent 0.4.0 jar produit et intègre" || fail "agent jar absent/corrompu"

( cd "$REPO/plugin" && cmd.exe /c "C:\\Users\\space\\buildtools\\apache-maven-3.9.6\\bin\\mvn.cmd -q -B clean package" ) > "$REPO/harness/.mvn-plugin.log" 2>&1
[ -s "$REPO/plugin/target/Irium-0.3.0.jar" ] && pass "plugin 0.3.0 jar produit" || fail "plugin jar absent"

# --- module de labo : compilé CONTRE L'API AGENT ---
echo "=== [1] Module labo HelloModule ==="
mkdir -p "$(dirname "$MODULE_OUT")"
( cd "$REPO/harness" && "$JAVAC" -cp "$AGENT_JAR" -d .lab-module HelloModule.java ) 2>&1 | head -3
if [ -f "$REPO/harness/.lab-module/HelloModule.class" ]; then
  cp "$REPO/harness/.lab-module/HelloModule.class" "$MODULE_OUT"
  pass "HelloModule.class compilé -> modules/hello.irm ($(stat -c%s "$MODULE_OUT") octets)"
else
  fail "compilation HelloModule"
fi

# --- serveur : redéploie le plugin fraîchement buildé et redémarre ---
echo "=== [1b] Serveur : déploiement plugin 0.3.0 + restart ==="
SERVER_DIR="/c/Users/space/irium-test/server"
# tuer le serveur existant (taskkill natif — le kill MSYS ne tue pas les enfants)
PID=$(netstat -ano | grep ":25599.*LISTENING" | head -1 | awk '{print $NF}')
[ -n "$PID" ] && cmd.exe /c "taskkill /F /T /PID $PID" >/dev/null 2>&1
sleep 2
rm -f "$SERVER_DIR"/plugins/Irium-*.jar
cp "$REPO/plugin/target/Irium-0.3.0.jar" "$SERVER_DIR/plugins/"
( cd "$SERVER_DIR" && "$JAVA" -Xmx2G -jar server.jar nogui > "$REPO/harness/.server.log" 2>&1 & )
# attendre un boot FRAIS : latest.log réécrit (mtime > marqueur) + "Done" + port
MARKER="$LAB_DIR/.boot-marker"; touch "$MARKER"
READY=""
for w in $(seq 1 90); do
  if [ "$SERVER_DIR/logs/latest.log" -nt "$MARKER" ] && grep -q "Done (" "$SERVER_DIR/logs/latest.log" 2>/dev/null && netstat -ano | grep -q ":25599.*LISTENING"; then READY="ok"; break; fi
  sleep 1
done
[ -n "$READY" ] && pass "serveur relancé avec Irium 0.3.0" || fail "serveur n'a pas démarré"

# --- client VanillaNoIq (ne connaît rien d'Irium) ---
echo "=== [2] Client labo ==="
CP="$(cat "$NETTY_CP_FILE")"
rm -rf "$LAB_DIR"; mkdir -p "$LAB_DIR"
( cd "$REPO/harness" && "$JAVAC" -cp "$CP" -d .lab VanillaNoIq.java ) 2>&1 | grep -vE "deprecat|Note" | head -3
[ -f "$LAB_DIR/VanillaNoIq.class" ] && pass "VanillaNoIq compilé" || fail "compilation VanillaNoIq"

# --- Test principal : AVEC agent, module poussé + exécuté ---
echo "=== [3] Test : agent + module streamé ==="
MARK=$(wc -l < "$LOG")
timeout 30 "$JAVA" -javaagent:"$AGENT_JAR"=force -cp "$LAB_CP;$CP" VanillaNoIq > "$LAB_DIR/agent-run.log" 2>&1
sleep 2
tail -n +$((MARK+1)) "$LOG" > "$LAB_DIR/newlog.txt"
grep -q "client classé: VanillaNoIq = AGENT v0.4.0" "$LAB_DIR/newlog.txt" && pass "serveur: classé AGENT v0.4.0" || fail "classification AGENT absente"
grep -q "module 'hello' poussé -> VanillaNoIq" "$LAB_DIR/newlog.txt" && pass "serveur: module hello poussé" || fail "push module absent"
grep -q "event 'hello' de VanillaNoIq" "$LAB_DIR/newlog.txt" && pass "serveur: EVENT reçu du module" || fail "event module non reçu"
grep -q "HelloModule ACTIF" "$LAB_DIR/agent-run.log" && pass "client: HelloModule exécuté" || fail "module non exécuté client"
grep -q "session fermée : 1 module" "$LAB_DIR/agent-run.log" && pass "client: sandbox fermée (1 module désactivé)" || fail "fermeture sandbox absente"

# --- Test corruption : octets falsifiés APRÈS hachage -> refus ---
echo "=== [4] Test : falsification en transit -> refus de chargement ==="
PID=$(netstat -ano | grep ":25599.*LISTENING" | head -1 | awk '{print $NF}')
[ -n "$PID" ] && cmd.exe /c "taskkill /F /T /PID $PID" >/dev/null 2>&1
sleep 2
( cd "$SERVER_DIR" && "$JAVA" -Xmx2G -Dirium.test.tamper=true -jar server.jar nogui > "$REPO/harness/.server-tamper.log" 2>&1 & )
MARKER="$LAB_DIR/.boot-marker2"; touch "$MARKER"
READY=""
for w in $(seq 1 90); do
  if [ "$SERVER_DIR/logs/latest.log" -nt "$MARKER" ] && grep -q "Done (" "$SERVER_DIR/logs/latest.log" 2>/dev/null && netstat -ano | grep -q ":25599.*LISTENING"; then READY="ok"; break; fi
  sleep 1
done
[ -n "$READY" ] && pass "serveur relancé en mode falsification" || fail "serveur tamper n'a pas démarré"
timeout 30 "$JAVA" -javaagent:"$AGENT_JAR"=force -cp "$LAB_CP;$CP" VanillaNoIq > "$LAB_DIR/corrupt-run.log" 2>&1
sleep 2
grep -q "sha256 MISMATCH -> refus" "$LAB_DIR/corrupt-run.log" && pass "client: refus sur octets falsifiés" || fail "pas de refus sur falsification"
grep -q "session fermée : 0 module" "$LAB_DIR/corrupt-run.log" && pass "client: AUCUN module chargé (0)" || fail "module chargé malgré falsification"

echo ""
echo "BILAN: $PASS PASS, $FAIL FAIL"
[ $FAIL -eq 0 ]
