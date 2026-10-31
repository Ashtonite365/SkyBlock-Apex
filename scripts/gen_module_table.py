#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Generates the single module/tag table (phase O, DESIGN.md 3.5).

Input:  scripts/modules/module-decisions.tsv (hand-edited: one row per holder, plus tagged options and screens)
        src/main/**.kt (ManagedConfig holders, MenuSettings custom menus, option keys, master toggles)
Output: src/main/resources/assets/apex/modules/modules.tsv

usage: python3 scripts/gen_module_table.py [--check]
  --check  only compare; exit 1 if modules.tsv is missing or out of date.
The build check (ModuleTableBuildCheckTest) re-checks the table against the registered holders at test time and
compares the source-sha256 header with the decisions file, so an edit without regenerating fails the suite.
"""
import hashlib, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
DECISIONS = ROOT / 'scripts/modules/module-decisions.tsv'
OUT = ROOT / 'src/main/resources/assets/apex/modules/modules.tsv'
KINDS = ('module', 'core', 'excluded', 'option', 'screen')
TAGS = ('allowed', 'red', 'purple')
RULE = re.compile(r'^(-|A[0-6]|R[1-4]|P[1-4]|C[12])(/(A[0-6]|R[1-4]|P[1-4]|C[12]))*$')
MASTERS = ('enabled', 'enable', 'enabled-toggle')
SKIP_SETS = ('test', 'gametest')

CFG = re.compile(r'(?:object|class)\s+(\w+)\s*(?:\([^)]*\))?\s*:\s*(?:[\w.]+\.)?ManagedConfig\(\s*(?:name\s*=\s*)?([^,]+?)\s*,\s*(?:category\s*=\s*)?(?:ManagedConfig\.)?Category\.(\w+)\s*\)')
CFG_NAMED = re.compile(r'(?:object|class)\s+(\w+)\s*(?:\([^)]*\))?\s*:\s*(?:[\w.]+\.)?ManagedConfig\(\s*category\s*=\s*(?:ManagedConfig\.)?Category\.(\w+)\s*,\s*name\s*=\s*([^,)]+?)\s*\)')
MENU = re.compile(r'object\s+(\w+)\s*:\s*MenuSettings\(\s*"([^"]+)"')
OPT = re.compile(r'\b(toggle|riskyToggle|riskySubToggle|customMenuAutoClick|autoClick|choice|integer|integerInput|duration|position|keyBinding|keyBindingWithDefaultUnbound|keyBindingWithOutDefaultModifiers|colour|string|button|dynamicButton)\s*(?:<[^>]*>)?\(\s*"([^"]+)"')


def strval(expr, text):
	expr = expr.strip()
	m = re.fullmatch(r'"([^"]+)"', expr)
	if m: return m.group(1)
	name = expr.split('.')[-1]
	m = re.search(r'\b%s\b\s*(?::\s*String)?\s*(?:get\(\)\s*)?=\s*"([^"]+)"' % re.escape(name), text) \
		or re.search(r'\b%s\b[^\n]*\n\s*get\(\)\s*=\s*"([^"]+)"' % re.escape(name), text)
	return m.group(1) if m else None


def scan_holders():
	"""holder name -> (relative file, [option keys]) for every holder in src/main."""
	holders, errors = {}, []
	files = [f for d in sorted((ROOT / 'src').iterdir()) if d.is_dir() and d.name not in SKIP_SETS for f in sorted(d.rglob('*.kt'))]
	for f in files:
		text = f.read_text(errors='ignore')
		cfgs = []
		found = [(m.start(), m.group(2)) for m in CFG.finditer(text)] + [(m.start(), m.group(3)) for m in CFG_NAMED.finditer(text)]
		for start, expr in found:
			name = strval(expr, text)
			if name is None: errors.append(f'{f.relative_to(ROOT)}: cannot resolve holder name {expr.strip()}')
			else: cfgs.append((start, name))
		cfgs += [(m.start(), 'custom-menu-' + m.group(2)) for m in MENU.finditer(text)]
		cfgs.sort()
		for _, name in cfgs:
			holders.setdefault(name, (str(f.relative_to(ROOT)), []))
		for m in OPT.finditer(text):
			owner = [c for c in cfgs if c[0] < m.start()]
			if owner: holders[owner[-1][1]][1].append(m.group(2))
	return holders, errors


def read_decisions():
	rows, errors, header = [], [], None
	for n, line in enumerate(DECISIONS.read_text().splitlines(), 1):
		if not line or line.startswith('#'): continue
		f = line.split('\t')
		if header is None:
			header = f
			continue
		if len(f) != len(header): errors.append(f'decisions line {n}: {len(f)} columns, want {len(header)}'); continue
		rows.append(dict(zip(header, f), line=n))
	return rows, errors


def build():
	holders, errors = scan_holders()
	rows, errs = read_decisions()
	errors += errs
	seen = {}
	for r in rows:
		k = r['key']
		if k in seen: errors.append(f"decisions line {r['line']}: duplicate key {k} (line {seen[k]})")
		seen[k] = r['line']
		if r['kind'] not in KINDS: errors.append(f"{k}: unknown kind {r['kind']}")
		if r['tag'] not in TAGS: errors.append(f"{k}: unknown tag {r['tag']}")
		if not RULE.match(r['rule']): errors.append(f"{k}: unknown rule {r['rule']}")
		if r['ask'] not in ('', 'ashton'): errors.append(f"{k}: ask must be empty or 'ashton'")
	top = {r['key']: r for r in rows if r['kind'] in ('module', 'core', 'excluded')}
	for name in sorted(set(holders) - set(top)): errors.append(f'holder {name} ({holders[name][0]}) has no decisions row')
	for name in sorted(set(top) - set(holders)): errors.append(f'decisions row {name} names no holder in src/')
	for r in rows:
		if r['kind'] == 'option':
			holder, _, opt = r['key'].partition('.')
			if holder not in holders or opt not in holders[holder][1]: errors.append(f"option row {r['key']} is not an option in code")
		deps = [d for d in r['deps'].split(',') if d]
		for d in deps:
			if top.get(d, {}).get('kind') not in ('module', 'core'): errors.append(f"{r['key']}: unknown dependency {d}")
		if deps and r['kind'] != 'module': errors.append(f"{r['key']}: only modules have dependencies")
		if r['master'] and r['kind'] != 'module': errors.append(f"{r['key']}: only modules have a master")
		elif r['master'] and r['master'] not in holders.get(r['key'], ('', []))[1]: errors.append(f"{r['key']}: master {r['master']} is not an option")
	graph = {k: [d for d in r['deps'].split(',') if d] for k, r in top.items()}
	state = {}
	def visit(k, path):
		if state.get(k) == 1: errors.append('dependency cycle: ' + ' -> '.join(path + [k])); return
		if state.get(k) == 2: return
		state[k] = 1
		for d in graph.get(k, []): visit(d, path + [k])
		state[k] = 2
	for k in sorted(graph): visit(k, [])
	if errors: return None, errors
	rank = {t: i for i, t in enumerate(TAGS)}
	opt_tags = {}
	for r in rows:
		if r['kind'] == 'option': opt_tags.setdefault(r['key'].split('.')[0], []).append(r['tag'])
	out = []
	for r in sorted(rows, key=lambda r: (r['key'].split('.')[0], r['kind'] not in ('module', 'core', 'excluded'), r['key'])):
		if r['kind'] in ('module', 'core', 'excluded'):
			eff = max([r['tag']] + opt_tags.get(r['key'], []), key=rank.get)
			master = r['master'] or next((o for o in holders[r['key']][1] if o in MASTERS), '')
		else:
			eff, master = r['tag'], ''
		out.append([r['key'], r['kind'], r['category'], r['tag'], eff, r['rule'], r['deps'], master, r['ask'], r['note']])
	sha = hashlib.sha256(DECISIONS.read_bytes()).hexdigest()
	text = ''.join([
		'# SPDX-License-Identifier: GPL-3.0-or-later\n',
		'# GENERATED by scripts/gen_module_table.py from scripts/modules/module-decisions.tsv. Do not edit; edit the decisions and regenerate.\n',
		f'# source-sha256: {sha}\n',
		'# tag = the row\'s own tag; effective = highest of the row and its option rows (what the module list shows, DESIGN 3.3).\n',
		'key\tkind\tcategory\ttag\teffective\trule\tdeps\tmaster\task\tnote\n',
	] + ['\t'.join(r) + '\n' for r in out])
	return text, []


def main():
	text, errors = build()
	if errors:
		for e in errors: print('error:', e, file=sys.stderr)
		sys.exit(2)
	if '--check' in sys.argv[1:]:
		if not OUT.exists() or OUT.read_text() != text:
			print(f'{OUT.relative_to(ROOT)} is out of date: run python3 scripts/gen_module_table.py', file=sys.stderr)
			sys.exit(1)
		print('module table up to date')
		return
	OUT.parent.mkdir(parents=True, exist_ok=True)
	OUT.write_text(text)
	n = text.count('\n') - 5
	print(f'wrote {OUT.relative_to(ROOT)}: {n} rows')


if __name__ == '__main__':
	main()
