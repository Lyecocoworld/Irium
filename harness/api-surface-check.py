#!/usr/bin/env python3
"""Vérificateur de surface API — M7-X2.

Pour chaque mod: javap -c extrait toutes les réf. Method/InterfaceMethod vers
net/fabricmc/** (nom + descripteur exact), puis vérifie que l'agent définit
chaque méthode. Tout mismatch = NoSuchMethodError garanti au runtime.

Usage: python3 harness/api-surface-check.py <agent.jar> <mod.jar> [mod.jar...]
"""
import sys, zipfile, subprocess, collections, os, re

JAVAP = r'C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot\bin\javap.exe'
REF = re.compile(r'// (?:Interface)?Method (net/fabricmc/[A-Za-z0-9/$_.]+)\.([A-Za-z0-9_$]+|<init>|<clinit>):({[^}]*}|\([^)]*\).+)')

PRIM = {'V':'void','Z':'boolean','B':'byte','C':'char','S':'short','I':'int','J':'long','F':'float','D':'double'}

def desc_clean(d):
    args, i = [], 1
    if d.startswith('{'):
        # descripteur générique {type}classe — normaliser
        inner = d[1:d.index('}')]
        d2 = inner + d[d.index('}')+1:]
        return desc_clean(d2.replace(inner, 'Ljava/lang/Object;') if inner and inner[0] not in 'L([' else d2)
    while d[i] != ')':
        arr = ''
        while d[i] == '[':
            arr += '[]'; i += 1
        c = d[i]
        if c == 'L':
            j = d.index(';', i)
            name = d[i+1:j].replace('/', '.'); i = j
        else:
            name = PRIM.get(c, c); i += 1
        args.append(name + arr)
    ret = d[i+1:]
    if ret.startswith('L'):
        ret = ret[1:-1].replace('/', '.')
    elif ret.startswith('['):
        base = ret[1:]
        ret = '[]' + (base[1:-1].replace('/', '.') if base.startswith('L') else PRIM.get(base, base))
    else:
        ret = PRIM.get(ret, ret)
    return args, ret

def agent_surface(agent_jar):
    """{(classe_interne): {('name', ('arg1','arg2'), 'ret')}} depuis javap -p."""
    z = zipfile.ZipFile(agent_jar)
    classes = [n[:-6] for n in z.namelist() if n.startswith('net/fabricmc/') and n.endswith('.class') and '$' not in n]
    surface = collections.defaultdict(set)
    for i in range(0, len(classes), 40):
        batch = classes[i:i+40]
        out = subprocess.run([JAVAP, '-p', '-cp', agent_jar] + batch,
                             capture_output=True, text=True, timeout=180).stdout
        cur = None
        for ln in out.splitlines():
            t = ln.strip()
            if not t or t.startswith(('Compiled from', '}', 'import')):
                continue
            sig = t.rstrip('{;').strip()
            m = re.search(r'\b(?:class|interface|enum|record)\s+([\w.$]+)', sig)
            if m and '(' not in sig and '=' not in sig:
                cur = m.group(1).split('<')[0]
                continue
            if cur and '(' in sig:
                surface[cur].add(sig)
    return surface

def match(sig_set, name, desc):
    """Une signature javap 'name(args)' matche-t-elle le descripteur ?"""
    args, ret = desc_clean(desc)
    want = name + '(' + ', '.join(args) + (')' if args else ')')
    want_nosp = name + '(' + ''.join(args) + ')'
    for s in sig_set:
        if not s.startswith(name + '('):
            continue
        # extraire les params javap
        body = s[s.index('(')+1:s.rindex(')')]
        # couper au top-level (les génériques contiennent des , et des <>)
        params, depth, cur2 = [], 0, ''
        for ch in body:
            if ch == '<': depth += 1
            if ch == '>': depth -= 1
            if ch == ',' and depth == 0:
                params.append(cur2.strip()); cur2 = ''
            else:
                cur2 += ch
        if cur2.strip(): params.append(cur2.strip())
        # comparer en ignorant les génériques et les espaces
        norm = [p.split('<')[0] for p in params]
        target = [a.split('<')[0] for a in args]
        if norm == target:
            return True
    return False

def main():
    agent_jar, mod_jars = sys.argv[1], sys.argv[2:]
    surface = agent_surface(agent_jar)
    n_m = sum(len(v) for v in surface.values())
    print(f'Agent: {n_m} méthodes sur {len(surface)} classes net/fabricmc (top-level)')
    total_bad = 0
    for mj in mod_jars:
        z = zipfile.ZipFile(mj)
        classes = [n[:-6] for n in z.namelist() if n.endswith('.class') and not n.startswith(('META-INF/', 'irium-jij/'))]
        wanted = set()
        for i in range(0, len(classes), 40):
            batch = classes[i:i+40]
            p = subprocess.run([JAVAP, '-c', '-p', '-cp', mj] + batch,
                               capture_output=True, text=True, timeout=300)
            for m in REF.finditer(p.stdout):
                wanted.add(m.groups())
        bad = []
        for cls, name, desc in sorted(wanted):
            if cls.endswith('/package-info') or name in ('<init>', '<clinit>'):
                continue
            dotted = cls.replace('/', '.')
            sigs = surface.get(dotted) or surface.get(cls)
            if sigs is None:
                bad.append((cls, name, desc, 'CLASSE ABSENTE'))
            elif not match(sigs, name, desc):
                bad.append((cls, name, desc, 'MÉTHODE/DESCRIPTEUR ABSENT'))
        print(f'\n=== {os.path.basename(mj)}: {len(wanted)} refs, {len(bad)} manquantes ===')
        for cls, name, desc, why in bad:
            print(f'  {why}: {cls.replace("/", ".")}::{name}{desc}')
        total_bad += len(bad)
    print(f'\nTOTAL mismatches: {total_bad}')
    sys.exit(1 if total_bad else 0)

if __name__ == '__main__':
    main()
