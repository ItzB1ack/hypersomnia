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

if errors:
    print('\n'.join(errors))
    raise SystemExit(f'FAILED: {len(errors)} resource problems')
print(f'OK: {checked} JSON files, internal model/texture references, registered sound files and advancement texts')
