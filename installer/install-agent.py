#!/usr/bin/env python3
"""
Irium Agent Installer — injecte le javaagent Gateway dans le manifest de
version SKLauncher. L'agent survit ainsi à tous les boots/reboots : il vit
dans la ligne de commande JVM construite par le launcher.

Usage:
  python install-agent.py install <version-id>   # injecte l'agent
  python install-agent.py uninstall <version-id> # retire l'agent
  python install-agent.py status <version-id>    # état de l'injection

Le JAR agent est lu depuis C:/Users/space/Code/Irium/agent/target/ et copié
vers %USERPROFILE%/.irium/agent/irium-agent.jar (emplacement produit stable).
Un backup du manifest est créé à la première injection (.irium-bak).
"""
import json
import os
import shutil
import sys

AGENT_SRC = os.path.join(os.path.expanduser("~"), "Code", "Irium", "agent", "target")
AGENT_DST_DIR = os.path.join(os.path.expanduser("~"), ".irium", "agent")
AGENT_DST = os.path.join(AGENT_DST_DIR, "irium-agent.jar")
SKL = os.path.join(os.path.expanduser("~"), "AppData", "Roaming", ".sklauncher")


def find_agent_jar():
    cands = sorted(
        (f for f in os.listdir(AGENT_SRC) if f.startswith("irium-agent-") and f.endswith(".jar")),
        reverse=True,
    )
    if not cands:
        sys.exit(f"[install] aucun irium-agent-*.jar dans {AGENT_SRC} — build d'abord")
    return os.path.join(AGENT_SRC, cands[0]), cands[0]


def manifest_path(version_id):
    return os.path.join(SKL, "versions", version_id, version_id + ".json")


def agent_arg():
    return "-javaagent:" + AGENT_DST + "=gateway"


def cmd_install(version_id):
    src, name = find_agent_jar()
    os.makedirs(AGENT_DST_DIR, exist_ok=True)
    shutil.copy2(src, AGENT_DST)
    print(f"[install] agent {name} -> {AGENT_DST}")

    p = manifest_path(version_id)
    if not os.path.isfile(p):
        sys.exit(f"[install] manifest introuvable: {p}")
    bak = p + ".irium-bak"
    if not os.path.exists(bak):
        shutil.copy2(p, bak)
        print(f"[install] backup: {bak}")

    d = json.load(open(p, encoding="utf-8"))
    jvm = d["arguments"]["jvm"]
    arg = agent_arg()
    if arg in jvm:
        print("[install] déjà injecté — rien à faire")
        return
    # retirer tout ancien javaagent irium éventuel (upgrade de version)
    jvm[:] = [a for a in jvm if not (isinstance(a, str) and a.startswith("-javaagent:") and "irium" in a)]
    jvm.insert(0, arg)
    json.dump(d, open(p, "w", encoding="utf-8"), indent=2, ensure_ascii=True)
    print(f"[install] injecté dans {p}")
    print(f"[install] HEAD jvm[0] = {arg}")


def cmd_uninstall(version_id):
    p = manifest_path(version_id)
    d = json.load(open(p, encoding="utf-8"))
    jvm = d["arguments"]["jvm"]
    before = len(jvm)
    jvm[:] = [a for a in jvm if not (isinstance(a, str) and a.startswith("-javaagent:") and "irium" in a)]
    if len(jvm) == before:
        print("[uninstall] rien à retirer")
        return
    json.dump(d, open(p, "w", encoding="utf-8"), indent=2, ensure_ascii=True)
    print(f"[uninstall] javaagent retiré de {p}")


def cmd_status(version_id):
    p = manifest_path(version_id)
    if not os.path.isfile(p):
        print(f"[status] manifest introuvable: {p}")
        return
    d = json.load(open(p, encoding="utf-8"))
    jvm = d["arguments"]["jvm"]
    hits = [a for a in jvm if isinstance(a, str) and a.startswith("-javaagent:") and "irium" in a]
    print(f"[status] {version_id}: " + (hits[0] if hits else "PAS d'agent injecté"))
    print(f"[status] jar présent: {os.path.isfile(AGENT_DST)}")


if __name__ == "__main__":
    if len(sys.argv) < 3 or sys.argv[1] not in ("install", "uninstall", "status"):
        print(__doc__)
        sys.exit(1)
    {"install": cmd_install, "uninstall": cmd_uninstall, "status": cmd_status}[sys.argv[1]](sys.argv[2])
