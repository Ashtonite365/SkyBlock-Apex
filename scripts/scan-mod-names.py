#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Name scan: other mods' names and the old mod id must not appear outside the listed exception set.

rg (PCRE2) finds identifier/word matches; this script then drops the listed exceptions and prints every
remaining hit. Exit code 0 = clean. Matching is per identifier part (camelCase / snake_case / kebab-case
boundaries), so words that merely contain the letters (encoding, neutral, Godin) are not hits.

Usage: scripts/scan-mod-names.py [repo root]
"""
import json, os, re, subprocess, sys

ROOT = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), '..'))

LONG = ['firmament', 'skyhanni', 'notenoughupdates', 'skyblockaddons', 'skytils', 'skyblocker', 'elitebot', 'dungeonroomsmod', 'jarvis']
SHORT = ['odin', 'neu', 'sba', 'drm', 'elite', 'danker']
L = r'(?:(?<![A-Za-z])|(?<=[a-z0-9])(?=[A-Z]))'
R = r'(?:(?![a-z])(?![A-Z][A-Z])|(?=[A-Z][a-z]))'
def short_re(w):
    return '|'.join([
        r'(?<![A-Za-z])' + w + r'(?![a-z])',
        L + w.capitalize() + r'(?![a-z])',
        L + w.upper() + r'(?![a-z])(?![A-Z][A-Z])(?![A-Z]\b)',
    ])
PATTERN = '(?i:' + '|'.join(LONG) + ')|' + '|'.join(short_re(w) for w in SHORT) + r'|Dungeon Rooms Mod'

EXEMPT_PATHS = [
    r'^LICENSES/', r'^LICENSE$', r'^NOTICE$', r'^CREDITS\.md$', r'(^|/)[^/]*LICENSE[^/]*$', r'\.license$',
    r'^REUSE\.toml$', r'(^|/)REUSE\.toml$',
    r'^src/main/resources/META-INF/apex/(CREDITS\.md|credits\.json)$',
    r'^src/main/kotlin/features/credits/',
    r'^src/main/kotlin/features/misc/LicenseViewer\.kt$',
    r'^src/main/kotlin/util/data/LegacyDataMigration\.kt$',
    r'^src/test/kotlin/util/data/LegacyDataMigrationTest\.kt$',
    r'^src/test/kotlin/util/data/KeyMappingMigrationTest\.kt$',
    r'^scripts/scan-mod-names\.py$',
    r'^src/test/kotlin/features/menus/MenuSectionsTest\.kt$',
    r'^scripts/gen-credits\.py$',
    r'^src/main/resources/assets/apex/[^/]+/(SNAPSHOT\.txt|README\.md)$',
    r'^src/main/resources/assets/apex/old_look/old_look\.json$',
    r'^src/main/kotlin/util/plus/ShHeader\.md$',
    r'^\.git$',
    r'^(SPLIT|REWRITE_PLAN|STATUS-[A-Z0-9]+)\.md$',
    r'^README\.md$',
    r'^src/main/resources/fabric\.mod\.json$',
    r'^build-logic/src/main/kotlin/licenseinfo\.kt$',
    r'^third-party-licenses/',
    r'^src/test/kotlin/buildcheck/',
    r'^src/test/resources/apex-fixtures/item-wiki/',
    r'^src/test/resources/ours-core-golden/repo-un189lore\.txt$',
]
LICENCE_MARK = re.compile(r'SPDX|Copyright|©|\([cC]\)|[Ll]icen[cs]e|LGPL|GPL|BSD|Apache|MIT\b')
DATA_CREDIT = re.compile(r'"_(source|credit|licen[cs]e|attribution)"\s*:')
ALLOWED = [
    r'https?://[^\s"\'<>)\]`]+',
    r'io[./]github[./]moulberry[\w./]*',
    r'io[./]github[./]notenoughupdates[./]moulconfig[\w./]*',
    r'org\.notenoughupdates[\w.:-]*', r'notenoughupdates\.org',
    r'\bNEU(?:Repository|RepositoryException|RepoFile|Item|Items|Ingredient|Recipe|CraftingRecipe|ForgeRecipe|'
    r'KatUpgradeRecipe|MobDropRecipe|NpcShopRecipe|TradeRecipe)\b',
    r'\bNEU_SENTINEL_(?:COINS|EMPTY)\b',
    r'"[^"\s]*NotEnoughUpdates-REPO[^"\s]*"',
    r'SkyHanni-REPO-main\b',
    r'NotEnoughUpdates/NotEnoughUpdates-REPO', r'"NotEnoughUpdates"',
    r'/resources/auctions/neu\b',
    r"Elite Voter's Badge",
    r'firmament:[\w/]*',
]
ALLOWED_RE = re.compile('|'.join(ALLOWED))
GLOBS = ['!build/**', '!**/build/**', '!.gradle/**', '!run/**', '!.git/**', '!**/*.png', '!**/*.webp', '!**/*.jar', '!**/*.ogg']

def header_lines(path, lines):
    """Indexes of the leading comment block (licence header) of a source file."""
    out = set()
    for i, ln in enumerate(lines):
        s = ln.strip()
        if s == '' or s.startswith(('//', '/*', '*', '#', '<!--', '-->')) or s.endswith('*/'):
            out.add(i); continue
        break
    return out

def main():
    cmd = ['rg', '--pcre2', '--json', '--hidden', '-e', PATTERN] + sum((['-g', g] for g in GLOBS), []) + ['.']
    res = subprocess.run(cmd, cwd=ROOT, capture_output=True, text=True)
    if res.returncode not in (0, 1):
        print(res.stderr); return 2
    hits, cache, files_scanned = [], {}, set()
    exempt = [re.compile(p) for p in EXEMPT_PATHS]
    for line in res.stdout.splitlines():
        ev = json.loads(line)
        if ev['type'] != 'match': continue
        path = ev['data']['path']['text'].removeprefix('./')
        if any(e.search(path) for e in exempt): continue
        text = ev['data']['lines'].get('text', '')
        n = ev['data']['line_number'] - 1
        if path not in cache:
            try: cache[path] = open(os.path.join(ROOT, path), encoding='utf-8', errors='replace').read().split('\n')
            except OSError: cache[path] = []
        if n in header_lines(path, cache[path]) and LICENCE_MARK.search(text): continue
        if DATA_CREDIT.search(text): continue
        stripped = ALLOWED_RE.sub(lambda m: ' ' * len(m.group()), text)
        found = [m.group() for m in re.finditer(PATTERN, stripped)]
        if found: hits.append((path, n + 1, sorted(set(found)), text.rstrip()[:200]))
    for p, n, w, t in hits: print(f'{p}:{n}: {",".join(w)}: {t.strip()}')
    print(f'scan-mod-names: {len(hits)} hit(s) in {len({h[0] for h in hits})} file(s) outside the exception set')
    return 1 if hits else 0

if __name__ == '__main__':
    sys.exit(main())
