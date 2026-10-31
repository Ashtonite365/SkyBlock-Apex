#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Phase O2: map every object that declares @Subscribe handlers to its owning module row, mechanically.

Output: src/main/resources/assets/apex/modules/owners.tsv (object, owner, rule). Rules, first match wins:
  file     exactly one holder is declared in the same file
  nested   the file declares several holders and exactly one encloses, or is enclosed by, the object
  package  exactly one holder is declared in the object's package (all source sets)
  holder   the file names exactly one holder object (e.g. Garden.TConfig, or the holder's object name)
  core     nothing matched, or the match is a core/excluded row: always-on owner (listed in phaseO/PROGRESS.md)
The owner is the module-table key, or "core". ModuleOwnersTest fails when an object with @Subscribe has no row here.
Usage: python3 scripts/gen_module_owners.py [--check]
"""
import collections, re, sys
from pathlib import Path
ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / 'scripts'))
import gen_module_table as g
OUT = ROOT / 'src/main/resources/assets/apex/modules/owners.tsv'
TABLE = ROOT / 'src/main/resources/assets/apex/modules/modules.tsv'
SKIP = {'test', 'gametest'}
HOLDER = re.compile(r'\b(?:object|class)\s+(\w+)[^{\n]*?:\s*(?:[\w.]+\.)?ManagedConfig\(([^)]*)\)')
TOK = re.compile(r'\b(object|class|interface)\s+(\w+)|[{}]|@Subscribe\b')


def strip_comments(t):
	t = re.sub(r'/\*[\s\S]*?\*/', '', t)
	return re.sub(r'(?m)^\s*//[^\n]*', '', t)


def strip(t):
	t = re.sub(r'"""[\s\S]*?"""', '""', t)
	t = re.sub(r'"(\\.|[^"\\\n])*"', '""', t)
	t = re.sub(r'/\*[\s\S]*?\*/', '', t)
	return re.sub(r'//[^\n]*', '', t)


def objects(text):
	"""(objects that directly hold @Subscribe, every declared path) in a stripped file."""
	stack, pending, subs, decls = [], None, [], []
	for m in TOK.finditer(text):
		s = m.group(0)
		if m.group(1): pending = (m.group(1), m.group(2))
		elif s == '{':
			stack.append(pending)
			if pending: decls.append('.'.join(x[1] for x in stack if x))
			pending = None
		elif s == '}':
			if stack: stack.pop()
		else:
			path = [x for x in stack if x]
			if path and path[-1][0] == 'object':
				name = '.'.join(x[1] for x in path)
				if name not in subs: subs.append(name)
	return subs, decls


def holder_name(args, text):
	parts = [a.strip() for a in args.split(',') if a.strip()]
	named = [a.split('=', 1)[1] for a in parts if re.match(r'name\s*=', a)]
	expr = named[0] if named else next((a for a in parts if '=' not in a), None)
	return g.strval(expr, text) if expr else None


def enclosing(text, pos):
	"""Names of the declarations enclosing [pos] in a stripped file, outermost first."""
	stack, pending = [], None
	for m in TOK.finditer(text[:pos]):
		s = m.group(0)
		if m.group(1): pending = m.group(2)
		elif s == '{': stack.append(pending); pending = None
		elif s == '}' and stack: stack.pop()
	return [x for x in stack if x]


def sources():
	return [f for d in sorted((ROOT / 'src').iterdir()) if d.is_dir() and d.name not in SKIP for f in sorted(d.rglob('*.kt'))]


def build():
	kinds = {}
	for line in TABLE.read_text().splitlines():
		if line.startswith('#') or line.startswith('key\t'): continue
		p = line.split('\t'); kinds[p[0]] = p[1]
	files = {f: f.read_text(errors='ignore') for f in sources()}
	pkg_of = {f: (re.search(r'^package\s+([\w.]+)', t, re.M) or [None, ''])[1] for f, t in files.items()}
	in_file, by_pkg, by_obj, by_outer = {}, collections.defaultdict(set), collections.defaultdict(set), collections.defaultdict(set)
	for f, t in files.items():
		hs, st = [], strip_comments(t)
		for m in HOLDER.finditer(st):
			name = holder_name(m.group(2), t)
			if name: hs.append((name, m.group(1), enclosing(st, m.start())))
		hs += [('custom-menu-' + m.group(2), m.group(1), enclosing(st, m.start())) for m in g.MENU.finditer(st)]
		in_file[f] = [(n, o) for n, o, _ in hs]
		for name, obj, outer in hs:
			by_pkg[pkg_of[f]].add(name)
			by_obj[obj if obj not in ('TConfig', 'DConfig', 'Settings', 'Config') else ''].add(name)
			if outer: by_outer[outer[-1]].add(name)
	rows = []
	for f, raw in files.items():
		body = strip(raw)
		subs, decls = objects(body)
		for o in subs:
			hs = in_file[f]; names = sorted({h[0] for h in hs}); rule, owner = 'core', 'core'
			if len(names) == 1: rule, owner = 'file', names[0]
			elif len(names) > 1:
				parts = o.split('.')
				inner = {n for n, obj in hs if obj in parts or any(d.startswith(o + '.') and d.endswith('.' + obj) for d in decls)}
				if len(inner) == 1: rule, owner = 'nested', inner.pop()
			if rule == 'core' and not names and len(by_pkg[pkg_of[f]]) == 1: rule, owner = 'package', next(iter(by_pkg[pkg_of[f]]))
			if rule == 'core' and not names:
				refs = set()
				for m in re.finditer(r'\b([A-Z]\w*)\b', body):
					if m[1] and m[1] in by_obj: refs |= by_obj[m[1]]
				for m in re.finditer(r'\b([A-Z]\w*)\.(?:TConfig|DConfig|Settings|Config)\b', body):
					refs |= by_outer.get(m[1], set())
				if len(refs) == 1: rule, owner = 'holder', refs.pop()
			if owner != 'core' and kinds.get(owner) != 'module': rule, owner = 'core', 'core'
			rows.append((pkg_of[f] + '.' + o, owner, rule))
	return sorted(rows)


def render(rows):
	head = ['# SPDX-License-Identifier: GPL-3.0-or-later',
		'# GENERATED by scripts/gen_module_owners.py (phase O2). Do not edit; regenerate.',
		'object\towner\trule']
	return '\n'.join(head + ['\t'.join(r) for r in rows]) + '\n'


if __name__ == '__main__':
	text = render(build())
	if '--check' in sys.argv:
		sys.exit(0 if OUT.is_file() and OUT.read_text() == text else 1)
	OUT.write_text(text)
	rows = [l.split('\t') for l in text.splitlines()[3:]]
	print(len(rows), 'objects;', collections.Counter(r[2] for r in rows))
