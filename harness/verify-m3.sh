#!/usr/bin/env bash
# ============================================================
# Irium M3 — harnais e2e canonique : l'agent rend un client
# vanilla compatible (question fondatrice du projet).
#
# Prérequis :
#   - serveur Canvas 26.2 lancé sur 127.0.0.1:25599 avec Irium-0.2.0.jar
#   - netty.cp présent (classpath netty 4.2.15 du serveur de test)
# Sortie : PASS/FAIL par test. Exit 0 si tout vert.
# ============================================================
set -u
JAVA="/c/Program Files/Eclipse Adoptium/jdk-25.0.3.9-hotspot/bin/java"
JAVAC="/c/Program Files/Eclipse Adoptium/jdk-25.0.3.9-hotspot/bin/javac"
REPO="$(cd "$(dirname "$0")/.." && pwd)"
# forme native Windows pour java.exe/javac.exe (REPO est MSYS /c/...)
REPO_W=$(echo "$REPO" | sed 's|^/\([a-z]\)/|\U\1:/|')
AGENT_JAR="$REPO_W/agent/target/irium-agent-0.3.0.jar"
NETTY_CP_FILE="/c/Users/space/irium-test/client/netty.cp"
LOG="/c/Users/space/irium-test/server/logs/latest.log"
LAB_DIR="$REPO/harness/.lab"
LAB_CP="$REPO_W/harness/.lab"
PASS=0; FAIL=0
pass(){ echo "PASS: $1"; PASS=$((PASS+1)); }
fail(){ echo "FAIL: $1"; FAIL=$((FAIL+1)); }

# --- build agent (Maven cmd.exe) ---
echo "=== [0] Build agent canonique ==="
for i in 1 2 3; do
  ( cd "$REPO/agent" && cmd.exe /c "C:\\Users\\space\\buildtools\\apache-maven-3.9.6\\bin\\mvn.cmd -q -B clean package" ) > "$REPO/harness/.mvn-agent.log" 2>&1
  # attendre que le jar soit stable et lisible (shade l'ecrit en place)
  J=""
  for w in 1 2 3 4 5 6 7 8 9 10; do
    if [ -s "$AGENT_JAR" ] && unzip -t "$AGENT_JAR" >/dev/null 2>&1; then J="ok"; break; fi
    sleep 1
  done
  [ -n "$J" ] && break
done
[ -n "$J" ] && pass "agent jar produit et intègre" || fail "agent jar absent/corrompu"

# --- compilation client labo ---
echo "=== [1] Client labo VanillaNoIq (aucune connaissance d'Irium) ==="
rm -rf "$LAB_DIR"; mkdir -p "$LAB_DIR"
CP="$(cat "$NETTY_CP_FILE")"
( cd "$REPO/harness" && "$JAVAC" -cp "$CP" -d .lab VanillaNoIq.java ) 2>&1 | grep -vE "deprecat|Note" | head -3
[ -f "$LAB_DIR/VanillaNoIq.class" ] && pass "VanillaNoIq compilé" || fail "compilation VanillaNoIq"
# le binaire ne doit contenir AUCUNE référence Irium
if unzip -p "$AGENT_JAR" 2>/dev/null | head -c0 >/dev/null; then :; fi
grep -c "irium" "$REPO/harness/VanillaNoIq.java" | grep -q "^0$" \
  && pass "source client sans référence irium" \
  || fail "source client contient 'irium'"

echo "=== [2] Test B : AVEC agent (doit être classé AGENT) ==="
MARK=$(wc -l < "$LOG")
timeout 25 "$JAVA" -javaagent:"$AGENT_JAR"=force -cp "$LAB_CP;$CP" VanillaNoIq > "$LAB_DIR/agent-run.log" 2>&1
sleep 2
tail -n +$((MARK+1)) "$LOG" > "$LAB_DIR/newlog.txt"
grep -q "client classé: VanillaNoIq = AGENT v0.3.0" "$LAB_DIR/newlog.txt" && pass "serveur: classé AGENT v0.3.0" || fail "classification AGENT absente"
grep -q "CHALLENGE irium" "$LAB_DIR/agent-run.log" && pass "agent: challenge vu" || fail "agent: challenge manqué"
grep -q "réponse AGENT envoyée" "$LAB_DIR/agent-run.log" && pass "agent: réponse envoyée" || fail "agent: pas de réponse"
grep -q "minecraft:register irium:hello envoyé" "$LAB_DIR/agent-run.log" && pass "agent: register envoyé" || fail "agent: register manquant"

echo "=== [3] Test A : SANS agent (doit être classé VANILLA) ==="
MARK=$(wc -l < "$LOG")
timeout 25 "$JAVA" -cp "$LAB_CP;$CP" VanillaNoIq > "$LAB_DIR/vanilla-run.log" 2>&1
sleep 2
tail -n +$((MARK+1)) "$LOG" > "$LAB_DIR/newlog2.txt"
grep -q "client classé: VanillaNoIq = VANILLA" "$LAB_DIR/newlog2.txt" && pass "serveur: classé VANILLA" || fail "classification VANILLA absente"
grep -q "CHALLENGE" "$LAB_DIR/vanilla-run.log" && fail "client seul: jamais challenge" || pass "client seul: jamais challenge"
grep -q "irium" "$LAB_DIR/vanilla-run.log" && fail "client seul: aucune trace irium" || pass "client seul: aucune trace irium"

echo ""
echo "BILAN: $PASS PASS, $FAIL FAIL"
[ $FAIL -eq 0 ]
