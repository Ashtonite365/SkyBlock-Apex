#!/usr/bin/env python3
"""Scan ManagedConfig holders/options/keybinds -> docs/rewrite/inventory.tsv (old key -> new path)."""
import re, pathlib, sys
root = pathlib.Path(__file__).resolve().parent.parent
CFG = re.compile(r'(?:object|class)\s+(\w+)\s*(?:\([^)]*\))?\s*:\s*ManagedConfig\(\s*([^,]+?)\s*,\s*(?:ManagedConfig\.)?Category\.(\w+)\s*\)')
OPT = re.compile(r'\b(toggle|riskyToggle|riskySubToggle|customMenuAutoClick|autoClick|choice|integer|integerInput|duration|position|keyBinding|keyBindingWithDefaultUnbound|keyBindingWithOutDefaultModifiers|colour|string|button|dynamicButton)\s*(?:<[^>]*>)?\(\s*"([^"]+)"')
def strval(expr, text):
    expr = expr.strip()
    m = re.fullmatch(r'"([^"]+)"', expr)
    if m: return m.group(1)
    name = expr.split('.')[-1]
    m = re.search(r'\b%s\b\s*(?::\s*String)?\s*(?:get\(\)\s*)?=\s*"([^"]+)"' % re.escape(name), text) \
        or re.search(r'\b%s\b[^\n]*\n\s*get\(\)\s*=\s*"([^"]+)"' % re.escape(name), text)
    return m.group(1) if m else '?' + expr
MAIN = {'TIMERS':'Timers','HUD':'HUD','INVENTORY':'GUI','AUCTION_BAZAAR':'GUI','PROFILE_VIEWER':'GUI','MUSEUM':'GUI',
        'FISHING':'Fishing','DUNGEONS':'Dungeons','KUUDRA':'Dungeons','GARDEN':'Farming','MINING':'Mining',
        'CUSTOM_MENUS':'Custom Menus'}
TIMER_SUB = [('fish','Fishing'),('dungeon','Dungeon'),('contest','Contest'),('jacob','Contest'),('event','Event'),
             ('mayor','Event'),('spooky','Event'),('calendar','Event')]
OVERRIDE = {
  'boss-tick-timers':('Timers','Dungeon'),'spirit-bear-timer':('Timers','Dungeon'),'terracotta-timer':('Timers','Dungeon'),
  'mining-event-timers':('Timers','Mining'),'lore-timers':('Timers','Items'),'ability-cooldowns':('Timers','Cooldowns'),
  'treecapitator':('Timers','Cooldowns'),
  'hud':('HUD','Layout'),'hud-style':('HUD','Style'),'custom-scoreboard':('HUD','Scoreboard'),'compact-tab-list':('HUD','Tab List'),
  'skill-overlay':('HUD','Skills'),'deployable-hud':('HUD','Deployables'),'sack-hud':('HUD','Sacks'),
  'stat-bars':('HUD','Stat Bars'),'stat-bars-action-bar':('HUD','Stat Bars'),'stat-bars-vanilla':('HUD','Stat Bars'),
  'solid-menus':('GUI','Menus'),'warp-menu':('GUI','Warp Menu'),'inventory-search':('GUI','Inventory Search'),
  'experimentation-table':('Experimentation','Experimentation Table'),'trevor':('Farming','Trevor'),
  'credits':('Miscellaneous','Credits'),
}
TIMER_KEY_SUB = [('golden-fish','Fishing'),('trevor','Farming'),('events','Event'),('jerry','Event'),('cake','Event'),
  ('experiments','Experimentation'),('mining','Mining'),('powder','Mining'),('mineshaft','Mining'),('puzzler','Mining'),
  ('fetchur','Mining'),('pickaxe','Mining'),('sky-mall','Mining'),('crimson','Crimson Isle'),('slayer','Slayer'),
  ('rift','Rift'),('abilities','Cooldowns'),('treecapitator','Cooldowns'),('cookie','Buffs'),('god-potion','Buffs'),
  ('heavy-pearls','Crimson Isle'),('infusion','Mining')]
def place_key(cat, cfg, path, key):
    if cfg == 'timers-overlay':
        for k, s in TIMER_KEY_SUB:
            if k in key: return 'Timers', s
        return 'Timers', 'General'
    return place(cat, cfg, path)
def place(cat, cfg, path):
    if cfg in OVERRIDE: return OVERRIDE[cfg]
    low = (cfg + ' ' + path).lower()
    if 'experiment' in low or 'superpair' in low or 'chronomatron' in low or 'ultrasequencer' in low:
        return 'Experimentation', cfg
    if cat == 'TIMERS':
        for k, s in TIMER_SUB:
            if k in low: return 'Timers', s
        return 'Timers', 'General'
    if cat == 'KUUDRA': return 'Dungeons', 'Kuudra / ' + cfg
    if cat == 'CUSTOM_MENUS' and cfg.startswith('custom-menu-'): return 'Custom Menus', cfg[len('custom-menu-'):]
    if cat in MAIN: return MAIN[cat], cfg
    if cat == 'FORAGING' and 'farm' in low: return 'Farming', cfg
    return 'Miscellaneous', cat.title().replace('_', ' ') + ' / ' + cfg
rows = []
MENU = re.compile(r'object\s+(\w+)\s*:\s*MenuSettings\(\s*"([^"]+)"(?:\s*,\s*panels\s*=\s*listOf\(([^)]*)\))?')
MENU_BASE = [('mode','choice'),('width','integer'),('height','integer'),('scale','integer'),('accent','colour')]
for f in sorted(root.glob('src/main/**/*.kt')):
    text = f.read_text(errors='ignore')
    rel = str(f.relative_to(root))
    if rel.endswith('features/menus/MenuSettings.kt'): continue
    cfgs = [(m.start(), m.group(1), strval(m.group(2), text), m.group(3)) for m in CFG.finditer(text)]
    for m in MENU.finditer(text):
        name = 'custom-menu-' + m.group(2)
        cfgs.append((m.start(), m.group(1), name, 'CUSTOM_MENUS'))
        sub = m.group(2)
        base = MENU_BASE + [('panel-' + p, 'toggle') for p in re.findall(r'"([^"]+)"', m.group(3) or '')]
        for k, kind in base:
            rows.append((name, k, kind + '(MenuSettings)', 'CUSTOM_MENUS', 'Custom Menus', sub, f'Custom Menus/{sub}/{k}', '', '', '', rel))
    cfgs.sort()
    if not cfgs: continue
    for m in OPT.finditer(text):
        owner = [c for c in cfgs if c[0] < m.start()]
        if not owner: continue
        _, obj, name, cat = owner[-1]
        kind = m.group(1)
        if kind == 'autoClick': kind = 'customMenuAutoClick'
        sub_toggle = kind == 'riskySubToggle'
        if sub_toggle: kind = 'riskyToggle'
        main, sub = place_key(cat, name, rel, m.group(2))
        kb = 'keybind' if kind.startswith('keyBinding') else ''
        risk = 'risky' if kind in ('riskyToggle', 'customMenuAutoClick') else ''
        default_on = 'on' if kind == 'customMenuAutoClick' else ('off' if kind == 'riskyToggle' else '')
        if sub_toggle: default_on = 'on (sub-option of quiz)' if m.group(2) == 'quiz-box' else 'sub-option'
        key = m.group(2)
        if '$' in key: kind += '(dynamic)'
        rows.append((name, key, kind, cat, main, sub, f'{main}/{sub}/{m.group(2)}', risk, default_on, kb, rel))
    for _, obj, name, cat in cfgs:
        if not any(r[0] == name for r in rows):
            main, sub = place(cat, name, rel)
            rows.append((name, '', 'holder-only', cat, main, sub, f'{main}/{sub}', '', '', '', rel))
MAIN_ID = {'Timers':'timers','GUI':'gui','HUD':'hud','Dungeons':'dungeons','Mining':'mining','Farming':'farming',
  'Fishing':'fishing','Custom Menus':'custom_menus','Experimentation':'experimentation','Miscellaneous':'misc'}
def slug(x): return re.sub(r'[^a-z0-9]+', '-', x.lower()).strip('-')
subs = {}
for r in rows: subs.setdefault((r[4], r[5]), set()).add(r[0])
mig = []
final = []
for r in rows:
    r = list(r)
    sub_id = slug(r[5].split(' / ')[-1])
    group = slug(r[5].split(' / ')[0]) if ' / ' in r[5] else ''
    shared = len(subs[(r[4], r[5])]) > 1
    owner_is_sub = r[0] == 'timers-overlay' or slug(r[0]) == sub_id
    prefix = f'{r[0]}.' if shared and not owner_is_sub else ''
    path = f'{MAIN_ID[r[4]]}.{sub_id}.{prefix}{r[1]}' if r[1] else f'{MAIN_ID[r[4]]}.{sub_id}'
    r[6] = path
    final.append(r)
    if r[1] and 'dynamic' not in r[2] and not r[2].startswith(('button', 'dynamicButton')):
        mig.append((f'{r[0]}.{r[1]}', path, r[2].split('(')[0], group))
rows = final
paths = [m[1] for m in mig]
dup = {p for p in paths if paths.count(p) > 1}
assert not dup, dup
EXTRA_ROWS = [
    ('fishing-helper.nearby-creatures-rare-first', 'fishing.fishing-helper.nearby-creatures-rare-first', 'toggle', ''),
    ('fishing-helper.nearby-creatures-separate', 'fishing.fishing-helper.nearby-creatures-separate', 'toggle', ''),
]
mig.extend(r for r in EXTRA_ROWS if r[0] not in {m[0] for m in mig})
paths = [m[1] for m in mig]
assert len(paths) == len(set(paths)), 'extra row collides'
PREFIX_RULES = [
    ('misc-extras.boost-*', 'misc.misc-extras.boost-*', 'integer', 'misc'),
    ('puzzle-solvers.spirit-leap-key-*', 'dungeons.puzzle-solvers.spirit-leap-key-*', 'keyBindingWithDefaultUnbound', ''),
    ('wardrobe-keybinds.slot-*', 'gui.wardrobe-keybinds.slot-*', 'keyBinding', 'pets-wardrobe'),
    ('loadout-keybinds.loadout-#', 'gui.loadout-keybinds.loadout-#', 'keyBindingWithDefaultUnbound', 'pets-wardrobe'),
    ('pet-keybinds.pet-#', 'gui.pet-keybinds.pet-#', 'keyBindingWithDefaultUnbound', 'pets-wardrobe'),
]
old = {m[0] for m in mig}
for r in PREFIX_RULES:
    rest = lambda k: k[len(r[0]) - 1:]
    clash = [k for k in old if k.startswith(r[0][:-1]) and rest(k) and (r[0][-1] == '*' or rest(k).isdigit())]
    assert not clash, (r[0], clash)
res = root / 'src/main/resources/assets/apex/settings/migration-map.tsv'
res.parent.mkdir(parents=True, exist_ok=True)
res.write_text('# SPDX-License-Identifier: GPL-3.0-or-later\n# Generated by scripts/rewrite_inventory.py. old_config.old_key<TAB>new_path<TAB>kind<TAB>group\n'
  + ''.join('\t'.join(m) + '\n' for m in sorted(set(mig)))
  + '# Prefix rules for keys built at runtime (REWRITE_PLAN.md section 7.6): old-prefix*<TAB>new-prefix* (# = digits only)\n'
  + ''.join('\t'.join(m) + '\n' for m in PREFIX_RULES))
print(len(set(mig)), 'migration entries', file=sys.stderr)
out = root / 'docs/rewrite/inventory.tsv'
out.write_text('config\tkey\tkind\told_category\tmain\tsubsection\tnew_path\trisk\trisk_default\tkeybind\tfile\n' +
               '\n'.join('\t'.join(r) for r in rows) + '\n')
print(len(rows), 'rows', len({r[0] for r in rows}), 'holders', sum(1 for r in rows if r[9]), 'keybinds', file=sys.stderr)
