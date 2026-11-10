#!/usr/bin/env python3
"""Проверка чертежей лаборатории main/resources/data/hypersomnia/lab/*.txt (оба мира).

    python3 tools/lab_check.py              проверить все чертежи
    python3 tools/lab_check.py --png DIR    ещё и превью PNG в DIR (нужен Pillow)

Правила повторяют LabBlueprint/LabBuilder: '=' и ворота — проём 3x3 (соседние клетки стены вырезаются, ещё через одну
должна быть стена); табличка, панель лифта, кодовая панель, щит и решётка вентиляции крепятся к первой стене по порядку
С, Ю, В, З (стена = '#' или заложенная дверь knock; табличка и панели без стены — к торцу высокой мебели R, rack, stack, equip),
стена не должна быть вырезана проёмом; кровать — изголовье у первой стены, изножье на полу напротив; капсула — горизонтальная
тройка CCC (западная клетка изножье, средняя середина, восточная изголовье, перед тройкой проход); стоячая капсула I —
колонка в три блока (высота этажа не меньше 3), рядом с ней должна быть достижимая клетка пола; ряды хранилища
(stack) и серверные стойки (rack) — сплошная линия одним символом по направлению роста номеров, перед лицом — проход;
оборудование (equip: crac|ups|battery и сторона лица) — перед лицом проход; директива syringe задаёт число
адреналиновых шприцев в шкафу (символ cabinet или обычный F/p) и должна ссылаться на существующие клетки шкафа.
Доступность: обход от '@' по полу; у каждой вещи, с которой работает игрок, должна быть достижимая клетка.
Предупреждения (не ошибки): нет перевода таблички в ru_ru, длинная строка таблички.
"""
import json, os, re, sys
from collections import deque

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LAB = os.path.join(ROOT, "main/resources/data/hypersomnia/lab")
JAVA = os.path.join(ROOT, "main/java/com/hypersomnia")
RU = os.path.join(ROOT, "main/resources/assets/hypersomnia/lang/ru_ru.json")

ORDER = ((0, -1), (0, 1), (1, 0), (-1, 0))          # С, Ю, В, З (LabBuilder.ORDER)
SIDE = {"N": (0, -1), "S": (0, 1), "E": (1, 0), "W": (-1, 0)}
STRUCT = set(" #.=L@Yl*Ew")
FURN = set("TRDdVZFphHCIvru+~")
EQUIP = ("crac", "ups", "battery")             # LabBlueprint.EQUIP
FLOOR = set(".*vru123456789")                          # LabBuilder.walkable: где можно встать
SIGN_LINE_WARN = 16
# Карты (station/finale/bed/capsule/panel/cabinet и родственные) строятся раньше мебели.
MAP_KINDS = ("station", "finale", "bed", "capsule", "panel", "knock", "rack", "equip", "stack", "cabinet")
# Символы, которые column() строит как стену, окно или кабину: карта на такой клетке не встанет.
STRUCTURAL = set("#=lL@YEw")


def tuning(name, default):
    try:
        src = open(os.path.join(JAVA, "lab/LabTuning.java"), encoding="utf-8").read()
        m = re.search(r"\b%s\s*=\s*(\d+)\s*;" % name, src)
        return int(m.group(1)) if m else default
    except OSError:
        return default


def known_docs():
    src = open(os.path.join(JAVA, "document/DocumentDefs.java"), encoding="utf-8").read()
    return set(re.findall(r'new Doc\("([a-z0-9_]+)"', src))


def known_stations():
    src = open(os.path.join(JAVA, "lab/ArchiveDefs.java"), encoding="utf-8").read()
    return set(re.findall(r'addStation\([^,]+,\s*"([a-z0-9_]+)"', src))


def sign_key(lines):
    s = re.sub(r"[^a-z0-9]+", "_", " ".join(lines).lower())
    return s.strip("_")


def parse(text):
    """-> (style, rows, maps): maps[kind][char] = строка значения (как в LabBlueprint)."""
    sec, rows, maps, style = 0, [], {}, "real"
    for line in text.split("\n"):
        line = line.rstrip("\r")
        if line == "---":
            sec += 1
            continue
        if sec == 1:
            rows.append(line)
            continue
        t = line.strip()
        if not t or t.startswith(";"):
            continue
        if sec == 0:
            if t.startswith("style "):
                style = t[6:].strip()
            continue
        if "=" not in t:
            continue
        k, v = [s.strip() for s in t.split("=", 1)]
        parts = k.split()
        if len(parts) == 2:
            maps.setdefault(parts[0], {})[parts[1][0]] = v
    if rows and rows[-1] == "":
        rows.pop()
    w = max(len(r) for r in rows)
    return style, [r.ljust(w) for r in rows], maps


class Floor:
    def __init__(self, name, text):
        self.name = name
        self.style, self.rows, self.maps = parse(text)
        self.d, self.w = len(self.rows), len(self.rows[0])
        m = self.maps
        self.signs = m.get("sign", {})
        self.locks = set(m.get("lock", {}))
        self.panels = set(m.get("panel", {}))
        self.stations = m.get("station", {})
        self.cabinets = m.get("cabinet", {})
        self.syringes = m.get("syringe", {})
        self.gates = set(m.get("gate", {}))
        self.finale = m.get("finale", {})
        self.capsules = set(m.get("capsule", {}))
        self.beds = set(m.get("bed", {}))
        self.knocks = set(m.get("knock", {}))
        self.racks = {c: v.split() for c, v in m.get("rack", {}).items()}
        self.stacks = {c: v.split() for c, v in m.get("stack", {}).items()}
        self.equips = {c: v.split() for c, v in m.get("equip", {}).items()}

    def at(self, x, z):
        return self.rows[z][x] if 0 <= z < self.d and 0 <= x < self.w else " "

    def cells(self, ch):
        return [(x, z) for z in range(self.d) for x in range(self.w) if self.rows[z][x] == ch]

    def solid(self, c):
        return c == "#" or c in self.knocks

    def tall(self, c):
        return c == "R" or c in self.racks or c in self.stacks or c in self.equips

    def first_wall(self, x, z):
        for dx, dz in ORDER:
            if self.solid(self.at(x + dx, z + dz)):
                return dx, dz
        return None


def check(name, text, docs=None, stations=None, ru=None):
    """-> (ошибки, предупреждения, Floor)."""
    f = Floor(name, text)
    at, errs, warns = f.at, [], []
    under = not name.endswith("_g")
    furn_maps = set(f.stations) | set(f.cabinets) | f.capsules | f.beds | set(f.racks) | set(f.stacks) | set(f.finale) | set(f.equips)
    special = furn_maps | f.locks | f.panels | f.gates | f.knocks

    # проёмы 3x3: вырезанные клетки стены и свободный пол по обе стороны
    carved, doors = set(), []
    for z in range(f.d):
        for x in range(f.w):
            c = at(x, z)
            if c != "=" and c not in f.gates:
                continue
            doors.append((x, z))
            along = at(x - 1, z) == "#" and at(x + 1, z) == "#"
            dx, dz = (1, 0) if along else (0, 1)
            for k in (-2, -1, 1, 2):
                if at(x + dx * k, z + dz * k) != "#":
                    errs.append("passage (%d,%d): wall cell %+d is '%s'" % (x, z, k, at(x + dx * k, z + dz * k)))
                elif abs(k) == 1:
                    carved.add((x + dx * k, z + dz * k))
            for k in (-1, 0, 1):
                for s in (-1, 1):
                    px, pz = x + dx * k + dz * s, z + dz * k + dx * s
                    p = at(px, pz)
                    if p == " " and not under:
                        continue
                    if p not in FLOOR and p not in "LY@":
                        errs.append("passage (%d,%d) blocked by '%s' at (%d,%d)" % (x, z, p, px, pz))

    # проём — ровно одна клетка-маркер: два соседних '='/ворота дали бы дыру шире 3
    markers = set(doors)
    for (x, z) in doors:
        for dx, dz in ORDER:
            nxt = (x + dx, z + dz)
            if nxt in markers and (x, z) < nxt:
                errs.append("passage (%d,%d) touches another passage marker at (%d,%d)" % (x, z, nxt[0], nxt[1]))

    # табличка не должна висеть вплотную к проёму: её читают с порога, а не из проёма
    for ch in f.signs:
        for (x, z) in f.cells(ch):
            close = [(px, pz) for (px, pz) in doors if max(abs(x - px), abs(z - pz)) < 2]
            if close:
                errs.append("sign %s at (%d,%d) is within 1 cell of passage (%d,%d)"
                            % (ch, x, z, close[0][0], close[0][1]))

    for z in range(f.d):
        for x in range(f.w):
            c = at(x, z)
            if c == " ":
                continue
            if not (c in STRUCT or c in FURN or c in special or (c.isdigit() and c in f.signs)):
                errs.append("unknown '%s' at (%d,%d)" % (c, x, z))
            if under and c not in "#w" and c not in f.finale and not f.solid(c):
                for dx, dz in ORDER:
                    if at(x + dx, z + dz) == " ":
                        errs.append("open edge at (%d,%d)" % (x, z))
            mount = c.isdigit() or c == "E" or c in f.locks
            if mount or c == "v" or c in f.panels:
                wo = f.first_wall(x, z)
                if wo is None:
                    if not (mount and any(f.tall(at(x + dx, z + dz)) for dx, dz in ORDER)):
                        errs.append("'%s' at (%d,%d): no wall to hang on" % (c, x, z))
                elif (x + wo[0], z + wo[1]) in carved:
                    errs.append("'%s' at (%d,%d) hangs on a carved doorway cell" % (c, x, z))
            if c in f.beds:
                wo = f.first_wall(x, z) or (-1, 0)
                fx, fz = x - wo[0], z - wo[1]
                if at(fx, fz) not in ".*ru":
                    errs.append("bed (%d,%d): foot cell (%d,%d) is '%s'" % (x, z, fx, fz, at(fx, fz)))
            if c in f.knocks:
                ns = f.solid(at(x, z - 1)) and f.solid(at(x, z + 1))
                ew = f.solid(at(x - 1, z)) and f.solid(at(x + 1, z))
                if not (ns or ew):
                    errs.append("knock (%d,%d) is not inside a wall line" % (x, z))

    # капсулы B5: только горизонтальные тройки CCC, западная клетка — изножье, восточная — изголовье
    seen = set()
    for z in range(f.d):
        x = 0
        while x < f.w:
            if at(x, z) != "C":
                x += 1
                continue
            x0 = x
            while at(x, z) == "C":
                x += 1
            if x - x0 != 3:
                errs.append("capsule (%d,%d): run of %d, need exactly 'CCC'" % (x0, z, x - x0))
            else:
                seen.update((x0 + i, z) for i in range(3))
    for z in range(f.d):
        for x in range(f.w):
            if at(x, z) == "C" and (x, z) not in seen:
                errs.append("capsule (%d,%d): not in a horizontal 'CCC' with the foot west" % (x, z))

    # стоячие капсулы (символ I): колонка в три блока должна влезть под потолок
    hm = re.search(r"^height\s+(\d+)", text, re.M)
    if f.cells("I") and (not hm or int(hm.group(1)) < 3):
        errs.append("upright capsule 'I' needs 'height' >= 3 (has %s)" % (hm.group(1) if hm else "none"))

    # карты важнее мебели: один символ не может быть двумя картами, карта не ставится
    # на стену/окно/кабину, а изножье кровати не должно затирать карту в соседней клетке
    map_kinds = {
        "station": set(f.stations), "finale": set(f.finale), "bed": f.beds,
        "capsule": f.capsules, "panel": f.panels, "knock": f.knocks,
        "rack": f.racks, "equip": f.equips, "stack": f.stacks,
        "cabinet": f.cabinets, "gate": f.gates, "lock": f.locks, "sign": set(f.signs),
    }
    owner = {}
    for kind, chars in map_kinds.items():
        for ch in chars:
            if ch in owner and owner[ch] != kind:
                errs.append("symbol '%s' is both %s and %s" % (ch, owner[ch], kind))
            owner.setdefault(ch, kind)
    for kind in MAP_KINDS + ("lock", "sign"):
        for ch in map_kinds[kind]:
            if ch in STRUCTURAL:
                errs.append("%s '%s' is a structural cell" % (kind, ch))
    map_cells = set()
    for kind in MAP_KINDS + ("lock",):
        for ch in map_kinds[kind]:
            map_cells.update(f.cells(ch))
    for ch in f.beds:
        for (x, z) in f.cells(ch):
            wo = f.first_wall(x, z) or (-1, 0)
            fx, fz = x - wo[0], z - wo[1]
            if (fx, fz) in map_cells:
                errs.append("bed %s at (%d,%d): foot (%d,%d) overwrites '%s'"
                            % (ch, x, z, fx, fz, at(fx, fz)))
    for ch, part in f.finale.items():
        if part != "bed":
            continue
        for (x, z) in f.cells(ch):
            wo = f.first_wall(x, z) or (-1, 0)
            fx, fz = x - wo[0], z - wo[1]
            if (fx, fz) in map_cells:
                errs.append("finale bed %s at (%d,%d): foot (%d,%d) overwrites '%s'"
                            % (ch, x, z, fx, fz, at(fx, fz)))
    # замок ставится после мебели: если его символ — мебель, furniture() съест клетку
    for ch in f.locks:
        if ch in FURN:
            errs.append("lock %s uses furniture symbol '%s'" % (ch, ch))

    # обход от центра кабины
    start = f.cells("@")
    reach = set()
    if start:
        walk = FLOOR | set("LY@lE=") | f.gates | f.locks | set(c for c, v in f.finale.items() if v in ("toy", "fragment", "door"))
        q = deque([start[0]])
        reach.add(start[0])
        while q:
            x, z = q.popleft()
            for dx, dz in ORDER:
                p = (x + dx, z + dz)
                if p not in reach and at(*p) in walk:
                    reach.add(p)
                    q.append(p)
        az = start[0]
        if under and at(az[0], az[1] + 2) != "l":
            errs.append("lift door is not 2 cells south of '@'")

    low = set("hHTDdru+") | set(f.stations)

    def near(x, z):
        """Можно дотянуться: достижимая клетка рядом (и по диагонали) или через одну низкую вещь (стул, стол)."""
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                if (dx or dz) and (x + dx, z + dz) in reach:
                    return True
        return any(at(x + dx, z + dz) in low and (x + 2 * dx, z + 2 * dz) in reach for dx, dz in ORDER)

    if start:
        for z in range(f.d):
            for x in range(f.w):
                c = at(x, z)
                what = None
                if c.isdigit() or c == "E":
                    if (x, z) not in reach:
                        what = "sign/panel"
                elif c in f.locks:
                    if (x, z) not in reach:
                        what = "lock"
                elif c in f.cabinets:
                    if not near(x, z):
                        what = "cabinet"
                elif c in f.stations or c in f.knocks or c in f.capsules or (
                        c in f.panels and not f.maps["panel"][c].startswith("-")):
                    if not near(x, z):
                        what = "station/knock/panel/capsule"
                elif c == "C":
                    if not near(x, z):
                        what = "capsule"
                elif c == "I":
                    if not any((x + dx, z + dz) in reach for dx, dz in ORDER):
                        what = "upright capsule"
                if what:
                    errs.append("unreachable %s '%s' at (%d,%d)" % (what, c, x, z))

    # ряды хранилища и стойки: линия, номера, проход перед лицом, табличка в голове линии
    def lines(kind, table):
        out = []
        for ch, v in table.items():
            if len(v) != 3 or v[1] not in SIDE or v[2] not in SIDE or not v[0].lstrip("-").isdigit():
                errs.append("%s %s: bad value %s" % (kind, ch, " ".join(v)))
                continue
            first, run, face = int(v[0]), SIDE[v[1]], SIDE[v[2]]
            cs = f.cells(ch)
            if not cs:
                errs.append("%s %s unused" % (kind, ch))
                continue
            head = min(cs, key=lambda p: p[0] * run[0] + p[1] * run[1])
            line = [(head[0] + run[0] * i, head[1] + run[1] * i) for i in range(len(cs))]
            if set(line) != set(cs):
                errs.append("%s %s: cells are not one straight line along %s" % (kind, ch, v[1]))
            for (x, z) in cs:
                fp = (x + face[0], z + face[1])
                if start and fp not in reach:
                    errs.append("%s %s at (%d,%d): front (%d,%d) unreachable" % (kind, ch, x, z, fp[0], fp[1]))
            hx, hz = head[0] - run[0], head[1] - run[1]
            if not at(hx, hz).isdigit():
                warns.append("%s %s: no sign at the head (%d,%d)" % (kind, ch, hx, hz))
            out.append((ch, first, len(cs)))
        return out

    st = lines("stack", f.stacks)
    if st:
        rows_ = sorted(n for _, n, _ in st)
        if rows_ != list(range(1, tuning("STACK_ROWS", 4) + 1)):
            errs.append("stack rows %s, expected 1..%d" % (rows_, tuning("STACK_ROWS", 4)))
        for ch, n, ln in st:
            if ln != tuning("STACK_PLACES", 8):
                errs.append("stack %s: %d places, expected %d" % (ch, ln, tuning("STACK_PLACES", 8)))
    rk = lines("rack", f.racks)
    if rk:
        nums = sorted(n + i for _, n, ln in rk for i in range(ln))
        if nums != list(range(1, tuning("RACKS", 12) + 1)):
            errs.append("rack numbers %s, expected 1..%d" % (nums, tuning("RACKS", 12)))

    # оборудование: вид, сторона лица, проход перед лицом
    for ch, v in f.equips.items():
        if len(v) != 2 or v[0] not in EQUIP or v[1] not in SIDE:
            errs.append("equip %s: bad value %s" % (ch, " ".join(v)))
            continue
        face = SIDE[v[1]]
        for (x, z) in f.cells(ch):
            fp = (x + face[0], z + face[1])
            if start and fp not in reach:
                errs.append("equip %s at (%d,%d): front (%d,%d) unreachable" % (ch, x, z, fp[0], fp[1]))

    # карты: документы, станции, неиспользованные символы
    for ch, doc in f.cabinets.items():
        if docs is not None and doc not in docs:
            errs.append("cabinet %s -> unknown doc %s" % (ch, doc))
    for ch, v in f.syringes.items():
        if not v.lstrip("+").isdigit() or int(v) < 1:
            errs.append("syringe %s: bad count %s" % (ch, v))
        elif ch not in f.cabinets and ch not in "Fp":
            errs.append("syringe %s: not a cabinet symbol" % ch)
    for ch, v in f.stations.items():
        if stations is not None and v.split()[0] not in stations:
            errs.append("station %s -> unknown station %s" % (ch, v))
    for kind in ("sign", "lock", "panel", "station", "cabinet", "syringe", "gate", "capsule", "bed", "knock", "finale", "equip"):
        for ch in f.maps.get(kind, {}):
            if not f.cells(ch):
                errs.append("%s %s unused" % (kind, ch))
    for ch, t in f.signs.items():
        if not ch.isdigit() or ch == "0":
            errs.append("sign %s: only 1-9" % ch)
        lines_ = t.split("|")
        if len(lines_) > 4:
            errs.append("sign %s: more than 4 lines" % ch)
        if ru is not None:
            key = "hypersomnia.lab.sign." + sign_key(lines_)
            if key not in ru:
                warns.append("ru_ru: no %s (%s)" % (key, t))
            elif any(len(s) > SIGN_LINE_WARN for s in ru[key].split("|")):
                warns.append("ru_ru: long line in %s" % key)
        if any(len(s) > SIGN_LINE_WARN for s in lines_):
            warns.append("sign %s: line longer than %d: %s" % (ch, SIGN_LINE_WARN, t))
    return errs, warns, f


def check_all(texts):
    """texts: имя -> текст чертежа. -> (ошибки, предупреждения) с именем чертежа."""
    docs, stations = known_docs(), known_stations()
    try:
        ru = json.load(open(RU, encoding="utf-8"))
    except OSError:
        ru = None
    errs, warns, used = [], [], {}
    for n in sorted(texts):
        e, w, f = check(n, texts[n], docs, stations, ru)
        errs += ["%s: %s" % (n, s) for s in e]
        warns += ["%s: %s" % (n, s) for s in w]
        for ch, doc in f.cabinets.items():
            used.setdefault((f.style, doc), []).append(n)
    for (style, doc), where in sorted(used.items()):
        if len(where) > 1:
            errs.append("%s world: doc %s in %s" % (style, doc, where))
    return errs, warns


COLORS = {"#": (60, 60, 64), ".": (200, 196, 186), "*": (250, 230, 120), "=": (150, 120, 90), "l": (150, 120, 90),
          "L": (170, 170, 190), "Y": (170, 170, 190), "@": (170, 170, 190), "E": (90, 90, 220), "w": (120, 170, 220),
          "R": (120, 80, 40), "D": (160, 110, 60), "d": (160, 110, 60), "V": (40, 40, 80),
          "Z": (110, 110, 140), "F": (130, 130, 120), "p": (130, 150, 110), "T": (180, 150, 110), "h": (200, 120, 60),
          "H": (220, 60, 60), "C": (235, 235, 235), "v": (150, 150, 150), "r": (240, 240, 240), "u": (200, 40, 40),
          "+": (90, 150, 150), "~": (230, 230, 245)}


def png(name, text, path, cell=14):
    from PIL import Image, ImageDraw
    f = Floor(name, text)
    img = Image.new("RGB", (f.w * cell, f.d * cell + 14), (20, 20, 20))
    dr = ImageDraw.Draw(img)
    dr.text((2, f.d * cell + 1), name, fill=(255, 255, 255))
    for z in range(f.d):
        for x in range(f.w):
            c = f.at(x, z)
            if c == " ":
                continue
            col, label = COLORS.get(c, (255, 0, 255)), ""
            if c.isdigit():
                col, label = (60, 120, 255), c
            elif c in f.locks:
                col, label = (230, 40, 40), "K"
            elif c in f.cabinets:
                col, label = (230, 140, 30), c
            elif c in f.stations:
                col, label = (40, 220, 220), "P"
            elif c in f.racks:
                col, label = (30, 160, 60), c
            elif c in f.stacks:
                col, label = (150, 60, 170), c
            elif c in f.knocks:
                col, label = (255, 0, 200), "k"
            elif c in f.beds:
                col, label = (255, 190, 210), "b"
            elif c in f.panels:
                col, label = (230, 200, 40), "B"
            elif c in f.equips:
                col, label = (150, 175, 190), f.equips[c][0][0]
            x0, z0 = x * cell, z * cell
            dr.rectangle([x0, z0, x0 + cell - 1, z0 + cell - 1], fill=col)
            if label:
                dr.text((x0 + 3, z0 + 1), label, fill=(0, 0, 0))
    img.save(path)


def main():
    texts = {f[:-4]: open(os.path.join(LAB, f), encoding="utf-8").read() for f in sorted(os.listdir(LAB)) if f.endswith(".txt")}
    errs, warns = check_all(texts)
    for w in warns:
        print("warn:", w)
    print("\n".join(errs) if errs else "OK: %d blueprints" % len(texts))
    if "--png" in sys.argv:
        out = sys.argv[sys.argv.index("--png") + 1]
        os.makedirs(out, exist_ok=True)
        for n, t in texts.items():
            png(n, t, os.path.join(out, n + ".png"))
    sys.exit(1 if errs else 0)


if __name__ == "__main__":
    main()
