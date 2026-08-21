#!/usr/bin/env python3
"""Audit de couverture Irium v2 — M7-X2.

Compare la surface net.fabricmc.* consommée par les mods du survey aux
stubs réellement présents dans l'agent. Sortie: gaps classés par fréquence.
Usage: python3 harness/coverage-audit.py [--survey DIR] [--agent DIR]
"""
import json, zipfile, re, sys, collections, os

SURVEY = sys.argv[sys.argv.index('--survey') + 1] if '--survey' in sys.argv else r'C:\Users\space\irium-test\modsurvey'
AGENT = sys.argv[sys.argv.index('--agent') + 1] if '--agent' in sys.argv else r'C:\Users\space\Code\Irium\agent\src\main\java'

PARSED_FIELDS = {'id', 'version', 'entrypoints', 'mixins', 'depends', 'custom',
                 'icon', 'name', 'description', 'authors', 'license', 'contact'}
IGNORED = {'schemaVersion', 'environment', 'provides', 'recommends', 'suggests',
           'conflicts', 'breaks', 'contributors', 'modmenu'}

# classes stubs existantes dans l'agent -> nom interne net/fabricmc/X
stubs = set()
for root, _, files in os.walk(os.path.join(AGENT, 'net', 'fabricmc')):
    for f in files:
        if f.endswith('.java'):
            rel = os.path.relpath(os.path.join(root, f), AGENT).replace('\\', '/')[:-5]
            stubs.add(rel)
# classes fournies par les MODS eux-mêmes (fabric-api JiJ skippé, mais les mods
# du survey embarquent parfois leurs api — on note juste les stubs agents)

unparsed = collections.Counter()
entrypoints = collections.Counter()
refs = collections.Counter()
refs_per_mod = {}
aw_users, jij_users = [], []

for fn in sorted(os.listdir(SURVEY)):
    if not fn.endswith('.jar'):
        continue
    z = zipfile.ZipFile(os.path.join(SURVEY, fn))
    try:
        fmj = json.loads(z.read('fabric.mod.json'))
    except KeyError:
        continue
    for k in fmj:
        if k not in PARSED_FIELDS and k not in IGNORED:
            unparsed[k] += 1
    if fmj.get('accessWidener'):
        aw_users.append(fn)
    if fmj.get('jars'):
        jij_users.append(fn)
    for key in (fmj.get('entrypoints') or {}):
        entrypoints[key] += 1
    mod_refs = set()
    for n in z.namelist():
        if not n.endswith('.class'):
            continue
        for m in re.finditer(rb'net/fabricmc/[A-Za-z0-9/$_.]+', z.read(n)):
            r = m.group(0).decode()
            refs[r] += 1
            mod_refs.add(r)
    refs_per_mod[fn] = mod_refs

print('=== CHAMPS fmj NON PARSÉS ===')
for k, c in unparsed.most_common():
    print(f'  {k}: {c}')
print(f'  accessWidener users: {len(aw_users)} | jars(JiJ) users: {len(jij_users)}')

print()
print('=== ENTRYPOINTS ===')
for k, c in entrypoints.most_common():
    print(f'  {k}: {c}')

# gap = classes référencées sans stub agent (au niveau classe top-level,
# une inner class manquante compte pour sa parente)
def top(r):
    return r.split('$')[0]

missing = collections.Counter()
for r, c in refs.items():
    t = top(r)
    t_java = t + '.java'
    if t_java not in stubs and t not in stubs:
        missing[t] += c

print()
print(f'=== GAPS: {len(missing)} classes SANS stub (par fréquence) ===')
for r, c in missing.most_common(50):
    mods = [fn.split('-')[0] for fn, s in refs_per_mod.items() if any(top(x) == r for x in s)]
    print(f'  {c:5d}  {r}   [{", ".join(sorted(set(mods))[:4])}]')

print()
print(f'STUBS existants: {len(stubs)} classes net.fabricmc.* dans l\'agent')
