#!/usr/bin/env python3
"""Basic offline checks; no Minecraft runtime required. Run from project root: python3 src/devtest/check_resources.py"""
import json
from pathlib import Path

root = Path(__file__).resolve().parents[2]
resources = root / 'src/main/resources'
assets = resources / 'assets/hypersomnia'
errors = []
checked = 0


def fail(path, message):
    errors.append(f'{path.relative_to(root)}: {message}')


def unique_pairs(pairs):
    obj = {}
    for k, v in pairs:
        if k in obj:
            raise ValueError(f'duplicate key {k!r}')
        obj[k] = v
    return obj

parsed = {}
for path in resources.rglob('*.json'):
    try:
        parsed[path] = json.loads(path.read_text(encoding='utf-8'), object_pairs_hook=unique_pairs)
        checked += 1
    except (OSError, ValueError) as exc:
        fail(path, str(exc))

for path in resources.rglob('*.mcmeta'):
    try:
        parsed[path] = json.loads(path.read_text(encoding='utf-8'), object_pairs_hook=unique_pairs)
        checked += 1
    except (OSError, ValueError) as exc:
        fail(path, str(exc))

for path, obj in parsed.items():
    if '/models/' not in path.as_posix():
        continue
    def walk(value, key=''):
        if isinstance(value, dict):
            for k, v in value.items(): walk(v, k)
        elif isinstance(value, list):
            for v in value: walk(v, key)
        elif isinstance(value, str) and value.startswith('hypersomnia:'):
            name = value.split(':', 1)[1]
            ref = assets / ('models' if key in ('parent', 'model') else 'textures') / (name + ('.json' if key in ('parent', 'model') else '.png'))
            if not ref.is_file(): fail(path, f'missing reference {value} ({key})')
    walk(obj)

for path, obj in parsed.items():
    posix = path.as_posix()
    if '/blockstates/' in posix:
        def walk_models(value, key=''):
            if isinstance(value, dict):
                for k, v in value.items(): walk_models(v, k)
            elif isinstance(value, list):
                for v in value: walk_models(v, key)
            elif isinstance(value, str) and key == 'model' and value.startswith('hypersomnia:'):
                name = value.split(':', 1)[1]
                if not (assets / 'models' / (name + '.json')).is_file():
                    fail(path, f'missing blockstate model {value}')
        walk_models(obj)
    elif '/particles/' in posix:
        for texture in obj.get('textures', []):
            if isinstance(texture, str) and texture.startswith('hypersomnia:'):
                name = texture.split(':', 1)[1]
                if not (assets / 'textures/particle' / (name + '.png')).is_file():
                    fail(path, f'missing particle texture {texture}')

sounds_path = assets / 'sounds.json'
for event, spec in parsed.get(sounds_path, {}).items():
    for sound in spec.get('sounds', []):
        name = sound if isinstance(sound, str) else sound.get('name', '')
        kind = 'file' if isinstance(sound, str) else sound.get('type', 'file')
        if kind != 'file' or not name.startswith('hypersomnia:'): continue
        if not (assets / 'sounds' / (name.split(':', 1)[1] + '.ogg')).is_file():
            fail(sounds_path, f'{event}: missing audio {name}')

english = parsed.get(assets / 'lang/en_us.json', {})
russian = parsed.get(assets / 'lang/ru_ru.json', {})
for path, obj in parsed.items():
    if '/advancement/' not in path.as_posix(): continue
    for part in ('title', 'description'):
        value = obj.get('display', {}).get(part, {})
        if isinstance(value, dict) and 'translate' in value:
            key = value['translate']
            if key not in english and not value.get('fallback'): fail(path, f'{part}: no English or fallback for {key}')
            if key not in russian: fail(path, f'{part}: missing Russian translation for {key}')

missing_en = sorted(set(russian) - set(english))
missing_ru = sorted(set(english) - set(russian))
if missing_en:
    fail(assets / 'lang/en_us.json', f'missing {len(missing_en)} keys from ru_ru.json, e.g. {", ".join(missing_en[:5])}')
if missing_ru:
    fail(assets / 'lang/ru_ru.json', f'missing {len(missing_ru)} keys from en_us.json, e.g. {", ".join(missing_ru[:5])}')

# --- text hygiene: placeholders, empty values, forbidden formatting ---------
import collections
import re

PLACEHOLDER = re.compile(r'%(?:%|\d+\$[sd]|[sd])')


def placeholders(value):
    return collections.Counter(PLACEHOLDER.findall(value))


for key in sorted(set(english) & set(russian)):
    en_value = english[key]
    ru_value = russian[key]
    if not isinstance(en_value, str) or not isinstance(ru_value, str):
        continue
    en_ph = placeholders(en_value)
    ru_ph = placeholders(ru_value)
    if en_ph != ru_ph:
        fail(assets / 'lang/en_us.json', f'{key}: placeholder mismatch en={dict(en_ph)} ru={dict(ru_ph)}')
    for lang, value in (('en_us', en_value), ('ru_ru', ru_value)):
        if not value.strip():
            fail(assets / f'lang/{lang}.json', f'{key}: empty value')
        if re.search(r'§[kmKM]', value):
            fail(assets / f'lang/{lang}.json', f'{key}: forbidden formatting code')

# --- unused keys: warning only ---------------------------------------------
corpus = ''
for path in (root / 'src/main/java').rglob('*.java'):
    corpus += path.read_text(encoding='utf-8', errors='replace')
for path in resources.rglob('*.json'):
    if '/lang/' in path.as_posix():
        continue
    corpus += path.read_text(encoding='utf-8', errors='replace')
literals = set(re.findall(r'"((?:[^"\\]|\\.)*)"', corpus))


def used_key(key):
    if key in corpus:
        return True
    parts = key.split('.')
    for i in range(2, len(parts) + 1):
        if '.'.join(parts[:i]) + '.' in literals:
            return True
    return False


unused = [key for key in sorted(english) if key.startswith('hypersomnia.') and not used_key(key)]
if unused:
    print(f'WARN: {len(unused)} unused lang keys, e.g. {", ".join(unused[:5])}')

# --- orphan assets: warning only -------------------------------------------
refs = set()
for path, obj in parsed.items():
    posix = path.as_posix()
    if '/models/' in posix:
        def walk_refs(value, key=''):
            if isinstance(value, dict):
                for k, v in value.items(): walk_refs(v, k)
            elif isinstance(value, list):
                for v in value: walk_refs(v, key)
            elif isinstance(value, str) and value.startswith('hypersomnia:'):
                name = value.split(':', 1)[1]
                refs.add(('models/' + name + '.json') if key in ('parent', 'model')
                         else ('textures/' + name + '.png'))
        walk_refs(obj)
    elif '/blockstates/' in posix:
        def walk_blockstate(value):
            if isinstance(value, dict):
                for k, v in value.items():
                    if k == 'model' and isinstance(v, str) and v.startswith('hypersomnia:'):
                        refs.add('models/' + v.split(':', 1)[1] + '.json')
                    else:
                        walk_blockstate(v)
            elif isinstance(value, list):
                for v in value: walk_blockstate(v)
        walk_blockstate(obj)
    elif '/particles/' in posix:
        for texture in obj.get('textures', []):
            if isinstance(texture, str) and texture.startswith('hypersomnia:'):
                refs.add('textures/particle/' + texture.split(':', 1)[1] + '.png')

sounds_path = assets / 'sounds.json'
for event, spec in parsed.get(sounds_path, {}).items():
    for sound in spec.get('sounds', []):
        name = sound if isinstance(sound, str) else sound.get('name', '')
        if name.startswith('hypersomnia:'):
            refs.add('sounds/' + name.split(':', 1)[1] + '.ogg')

orphans = []
for path in assets.rglob('*'):
    if not path.is_file() or path.suffix == '.mcmeta':
        continue
    rel = path.relative_to(assets).as_posix()
    if rel.startswith('lang/') or rel in refs:
        continue
    stem = rel.rsplit('.', 1)[0]
    ns = stem
    for prefix in ('textures/', 'models/', 'sounds/', 'blockstates/'):
        if stem.startswith(prefix):
            ns = stem[len(prefix):]
            break
    if stem in corpus or ns in corpus or ('hypersomnia:' + ns) in corpus or path.stem in corpus:
        continue
    # dynamic names: textures/misc/trace_<type>_<n>.png, ..._r.png
    dynamic = False
    for i, ch in enumerate(stem):
        if ch == '_' and stem[:i + 1] in corpus:
            dynamic = True
            break
    if not dynamic:
        orphans.append(rel)
if orphans:
    print(f'WARN: {len(orphans)} orphan assets, e.g. {", ".join(sorted(orphans)[:5])}')


if errors:
    print('\n'.join(errors))
    raise SystemExit(f'FAILED: {len(errors)} resource problems')
print(f'OK: {checked} JSON files, internal model/texture references, registered sound files,'
      f' advancement texts and {len(english)} lang keys (en/ru parity, placeholders, empty values, §k/§m)')
