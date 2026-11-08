#!/usr/bin/env python3
"""Checks Pixel Suite's translation files (res/raw/i18n_<lang>.txt) before a build.

usage: python3 i18n-check.py [project dir]
Each line is: English<TAB>translation, with \\n for a line break. It checks that every file has the same English
texts, the {0}, {1} … parts and **bold** marks match, no line is broken, and the languages match I18n.java.
Prints "i18n OK" and exits 0, or lists the problems and exits 1.
"""
import pathlib, re, sys
root = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else '.')
raw = root / 'res' / 'raw'
src = (root / 'src' / 'io' / 'github' / 'pixelsuite' / 'I18n.java').read_text(encoding='utf-8')
langs = re.findall(r'"(\w\w)"', re.search(r'LANGS\s*=\s*\{([^}]*)\}', src).group(1))
ph = re.compile(r'\{\d\}')
problems, keys = [], {}
for lang in langs:
    p = raw / ('i18n_' + lang + '.txt')
    if not p.exists(): problems.append(f'{p.name}: missing'); continue
    seen = {}
    for n, line in enumerate(p.read_text(encoding='utf-8').split('\n'), 1):
        if not line or line.startswith('#'): continue
        if line.count('\t') != 1: problems.append(f'{p.name}:{n}: needs exactly one tab'); continue
        en, tr = line.split('\t')
        if not tr.strip(): problems.append(f'{p.name}:{n}: empty translation')
        if en in seen: problems.append(f'{p.name}:{n}: same English text as line {seen[en]}')
        seen[en] = n
        if sorted(ph.findall(en)) != sorted(ph.findall(tr)): problems.append(f'{p.name}:{n}: {{0}} parts differ')
        if en.count('**') != tr.count('**'): problems.append(f'{p.name}:{n}: **bold** marks differ')
        if re.search(r'\\(?![nt\\])', tr.replace('\\\\', '')): problems.append(f'{p.name}:{n}: stray backslash')
    keys[lang] = set(seen)
    print(f'{p.name}: {len(seen)} texts')
if keys:
    first = next(iter(keys.values()))
    for lang, k in keys.items():
        if k != first: problems.append(f'i18n_{lang}.txt: {len(first - k)} texts missing, {len(k - first)} extra compared to i18n_{langs[0]}.txt')
for x in sorted(raw.glob('i18n_*.txt')):
    if x.stem[5:] not in langs: problems.append(f'{x.name}: not in I18n.LANGS')
if problems:
    print('\n'.join(problems)); print(len(problems), 'problem(s)'); sys.exit(1)
print('i18n OK:', len(langs), 'languages')
