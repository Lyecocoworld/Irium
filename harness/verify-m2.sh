#!/usr/bin/env bash
# ============================================================
# Irium M2 — harnais e2e canonique du handshake (versionné).
#
# Vérifie, sur un serveur Canvas 26.2 live avec Irium-0.2.0 :
#   1. le build Maven canonique depuis l'arbre commité
#   2. la parité des .class entre jar buildé et jar déployé
#   3. la classification e2e (bot agent -> AGENT, bot vanilla -> VANILLA)
#   4. l'hygiène des logs (aucun System.out Nag)
#   5. l'état git (arbre propre, HEAD == origin/main)
#
# Usage : bash harness/verify-m2.sh
# Prérequis : serveur de test lancé sur 127.0.0.1:25599
#             (voir README du harnais pour le setup complet)
# Sortie   : PASS/FAIL par contrôle, code sortie 0 si tout vert.
# ============================================================
set -u
JAVA="/c/Program Files/Eclipse Adoptium/jdk-25.0.3.9-hotspot/bin/java"
MVN="C:\\Users\\space\\buildtools\\apache-maven-3.9.6\\bin\\mvn.cmd"
REPO="$(cd "$(dirname "$0")/.." && pwd)"
SRV="/c/Users/space/irium-test/server"
LOG="$SRV/logs/latest.log"
PASS=0; FAIL=0
pass(){ echo "PASS: $1"; PASS=$((PASS+1)); }
fail(){ echo "FAIL: $1"; FAIL=$((FAIL+1)); }

# --- classpath bot : harnais versionné compilé dans un dossier fixe (MSYS-safe) ---
WORK="$REPO/harness/.work"
rm -rf "$WORK"; mkdir -p "$WORK"
JAVAC="/c/Program Files/Eclipse Adoptium/jdk-25.0.3.9-hotspot/bin/javac"
( cd harness && "$JAVAC" -d .work IriumBot.java Handshake.java ) \
  && pass "harnais compilé (IriumBot + Handshake)" \
  || fail "compilation harnais"
ls "$WORK"/IriumBot.class >/dev/null 2>&1 && pass "IriumBot.class présent" || fail "IriumBot.class absent"
BOTCP="C:/Users/space/Code/Irium/harness/.work"  # forme native pour java.exe

echo "=== [1] Build canonique (Maven, cmd.exe) ==="
( cd "$REPO/plugin" && cmd.exe /c "$MVN -q clean package" ) > "$WORK/mvn.log" 2>&1
MV=$?
[ $MV -eq 0 ] && pass "mvn clean package exit 0" || fail "mvn exit $MV"
grep -q "ERROR" "$WORK/mvn.log" && fail "aucun ERROR mvn" || pass "aucun ERROR mvn"
[ -s "$REPO/plugin/target/Irium-0.2.0.jar" ] && pass "jar produit" || fail "jar absent"

echo "=== [2] Parité jar déployé == jar buildé (sha256 des .class) ==="
for C in HandshakeListener IriumPlugin JoinListener; do
  P="dev/irium/plugin/$C.class"
  A=$(unzip -p "$REPO/plugin/target/Irium-0.2.0.jar" "$P" 2>/dev/null | sha256sum | cut -d' ' -f1)
  B=$(unzip -p "$SRV/plugins/Irium-0.2.0.jar" "$P" 2>/dev/null | sha256sum | cut -d' ' -f1)
  [ -n "$A" ] && [ "$A" = "$B" ] && pass "$C.class identique déployé/buildé" || fail "$C.class divergent"
done

echo "=== [3] E2E live (fenêtre de log fraîche) ==="
( echo > /dev/tcp/127.0.0.1/25599 ) 2>/dev/null && pass "serveur joignable :25599" || fail "serveur injoignable"
MARK=$(wc -l < "$LOG")
timeout 30 "$JAVA" -cp "$BOTCP" IriumBot agent 127.0.0.1 15000 > "$WORK/agent.log" 2>&1
sleep 2
timeout 30 "$JAVA" -cp "$BOTCP" IriumBot vanilla 127.0.0.1 12000 > "$WORK/vanilla.log" 2>&1
sleep 2
tail -n +$((MARK+1)) "$LOG" > "$WORK/newlog.txt"
grep -q "client classé: IriumAgent = AGENT v0.1.0" "$WORK/newlog.txt" && pass "serveur: bot agent = AGENT v0.1.0" || fail "classification AGENT absente"
grep -q "client classé: PlainVani = VANILLA" "$WORK/newlog.txt" && pass "serveur: bot vanilla = VANILLA" || fail "classification VANILLA absente"
grep -q "CHALLENGE irium" "$WORK/agent.log" && pass "bot agent: challenge vu" || fail "bot agent: challenge manqué"
grep -q "réponse AGENT envoyée" "$WORK/agent.log" && pass "bot agent: réponse envoyée" || fail "bot agent: pas de réponse"
grep -q "minecraft:register" "$WORK/vanilla.log" && fail "bot vanilla: silencieux (aucun register)" || pass "bot vanilla: silencieux (aucun register)"
grep -q "CHALLENGE" "$WORK/vanilla.log" && fail "bot vanilla: challenge jamais reçu" || pass "bot vanilla: challenge jamais reçu"
grep -q "Nag author" "$WORK/newlog.txt" && fail "zéro Nag System.out (fenêtre fraîche)" || pass "zéro Nag System.out (fenêtre fraîche)"

echo "=== [4] Git ==="
( cd "$REPO" && [ -z "$(git status --porcelain)" ] ) && pass "arbre propre" || fail "arbre sale"
( cd "$REPO" && [ "$(git rev-parse HEAD)" = "$(git rev-parse origin/main)" ] ) && pass "HEAD == origin/main" || fail "HEAD != origin/main"

echo ""
echo "BILAN: $PASS PASS, $FAIL FAIL"
[ $FAIL -eq 0 ]
