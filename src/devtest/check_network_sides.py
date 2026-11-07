#!/usr/bin/env python3
"""Check serverbound/common registrars do not directly link client payload handlers."""
from pathlib import Path
import re
root = Path(__file__).resolve().parents[1] / 'main/java/com/hypersomnia'
files = [root/'Hypersomnia.java'] + list((root/'network').glob('*Network.java'))
errors = []
for path in files:
    text = path.read_text(encoding='utf-8')
    if re.search(r'^import\s+(?:net\.minecraft\.client|com\.hypersomnia\.client)\.', text, re.M):
        errors.append(f'{path.name}: client import')
    if re.search(r'com\.hypersomnia\.client\.\w+::', text):
        errors.append(f'{path.name}: client method reference')
bridge = (root/'network/ClientPayloadBridge.java').read_text(encoding='utf-8')
setup = (root/'client/ClientPayloadSetup.java').read_text(encoding='utf-8')
handlers = set(re.findall(r'IPayloadHandler<\w+>\s+(\w+)Handler', bridge))
bound = set(re.findall(r'ClientPayloadBridge\.(\w+)Handler\s*=', setup))
if handlers != bound:
    errors.append(f'bridge/setup mismatch: missing={handlers-bound}, extra={bound-handlers}')
if 'value = Dist.CLIENT' not in setup:
    errors.append('client setup is not restricted to Dist.CLIENT')
if errors:
    raise SystemExit('\n'.join(errors))
print(f'OK: {len(files)} common registrars, {len(handlers)} client handler bindings')
