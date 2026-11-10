#!/usr/bin/env python3
"""Network side symmetry and hygiene check.

Rules:
- every CustomPacketPayload in network/ is registered exactly once;
- playToClient handlers go through ClientPayloadBridge (or a common lambda),
  playToServer handlers are static methods of a non-client class;
- every ClientPayloadBridge handler has a wrapper method and a client binding;
- common/network code does not import or reference client classes;
- every registrar declares an explicit channel version.
"""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
NET = ROOT / "main/java/com/hypersomnia/network"
JAVA = ROOT / "main/java"
errors = []

# --- payload classes -------------------------------------------------------
payloads = {}
for path in NET.glob("*.java"):
    text = path.read_text(encoding="utf-8")
    if "implements CustomPacketPayload" in text:
        payloads[path.stem] = path

# --- registrations ---------------------------------------------------------
registration = re.compile(
    r"playTo(Client|Server)\s*\(\s*(\w+)\.TYPE\s*,\s*(\w+)\.STREAM_CODEC\s*,\s*(.*?)\s*\)\s*;",
    re.S,
)
registrars = {}
seen = {}
for path in sorted(list(NET.glob("*.java")) + [JAVA / "com/hypersomnia/Hypersomnia.java"]):
    text = path.read_text(encoding="utf-8")
    if "playToClient" not in text and "playToServer" not in text:
        continue
    versions = re.findall(r"registrar\s*\(\s*\"([^\"]*)\"\s*\)", text)
    if not versions:
        errors.append(f"{path.name}: registrar without an explicit version")
    else:
        for version in versions:
            if not re.fullmatch(r"\d+(?:\.\d+)*", version):
                errors.append(f"{path.name}: bad channel version {version!r}")
        registrars[path.name] = versions
    for side, payload, codec, handler in registration.findall(text):
        handler = " ".join(handler.split())
        if payload != codec:
            errors.append(f"{path.name}: {payload} registered with {codec}.STREAM_CODEC")
        if payload not in payloads:
            errors.append(f"{path.name}: unknown payload {payload}")
            continue
        if payload in seen:
            errors.append(f"{payload}: registered twice ({seen[payload]}, {path.name})")
        seen[payload] = path.name
        if side == "Client":
            if not (handler.startswith("ClientPayloadBridge::") or "->" in handler):
                errors.append(f"{path.name}: {payload} client handler is not a bridge/lambda: {handler}")
        else:
            if handler.startswith("ClientPayloadBridge::") or "->" in handler:
                errors.append(f"{path.name}: {payload} server handler must be a static method: {handler}")
            m = re.fullmatch(r"(?:[\w.]+\.)?(\w+)::(\w+)", handler)
            if not m:
                errors.append(f"{path.name}: cannot parse server handler {handler}")
            else:
                cls, method = m.group(1), m.group(2)
                candidates = list(JAVA.rglob(cls + ".java"))
                if not candidates:
                    errors.append(f"{path.name}: handler class {cls} not found")
                else:
                    if any("client" in p.parts for p in candidates):
                        errors.append(f"{path.name}: server handler {cls} lives in client code")
                    ok = False
                    for candidate in candidates:
                        src = candidate.read_text(encoding="utf-8")
                        if re.search(r"static\s+[^;{}]*\b" + re.escape(method) + r"\s*\(", src):
                            ok = True
                            break
                    if not ok:
                        errors.append(f"{path.name}: handler method {cls}.{method} not found/static")

for name in sorted(payloads):
    if name not in seen:
        errors.append(f"{name}: payload has no registration")

# --- bridge and client bindings -------------------------------------------
bridge = (NET / "ClientPayloadBridge.java").read_text(encoding="utf-8")
setup = (JAVA / "com/hypersomnia/client/ClientPayloadSetup.java").read_text(encoding="utf-8")
handlers = set(re.findall(r"IPayloadHandler<\w+>\s+(\w+)Handler\s*=", bridge))
wrappers = set(re.findall(r"public static void (\w+)\([^)]*IPayloadContext", bridge))
bound = set(re.findall(r"ClientPayloadBridge\.(\w+)Handler\s*=", setup))
if handlers != wrappers:
    errors.append(f"bridge field/wrapper mismatch: missing={handlers - wrappers}, extra={wrappers - handlers}")
if handlers != bound:
    errors.append(f"bridge/setup mismatch: missing={handlers - bound}, extra={bound - handlers}")
if "value = Dist.CLIENT" not in setup:
    errors.append("client setup is not restricted to Dist.CLIENT")

client_refs = set()
registration_text = "".join(p.read_text(encoding="utf-8") for p in NET.glob("*.java"))
registration_text += (JAVA / "com/hypersomnia/Hypersomnia.java").read_text(encoding="utf-8")
for side, payload, codec, handler in registration.findall(registration_text):
    if side == "Client" and handler.strip().startswith("ClientPayloadBridge::"):
        client_refs.add(handler.strip().split("::", 1)[1])
if client_refs != wrappers:
    errors.append(f"registered bridge handlers != wrappers: missing={client_refs - wrappers}, extra={wrappers - client_refs}")

# --- client isolation in common/network -----------------------------------
for path in sorted(list(NET.glob("*.java")) + [JAVA / "com/hypersomnia/Hypersomnia.java"]):
    text = path.read_text(encoding="utf-8")
    if re.search(r"^import\s+(?:net\.minecraft\.client|com\.hypersomnia\.client)\.", text, re.M):
        errors.append(f"{path.name}: client import")
    if re.search(r"com\.hypersomnia\.client\.\w+::", text):
        errors.append(f"{path.name}: client method reference")
    if re.search(r"\bDist\.CLIENT\b", text) and path.name != "ClientPayloadSetup.java":
        errors.append(f"{path.name}: Dist.CLIENT in common network code")

if errors:
    raise SystemExit("\n".join(errors))
print(f"OK: {len(payloads)} payloads registered once, {len(handlers)} client handler bindings, "
      f"{len(registrars)} registrars with versions")
