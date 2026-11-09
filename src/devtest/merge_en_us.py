"""Дописывает в en_us.json английские ключи, которых нет в файле, но которые есть в ru_ru.json.

Источники:
  * build/en_us_fallback.tsv — вывод devtest-генератора EnUsDump (doc, archive, ending, finale,
    advancement): английский запасной текст из DocumentDefs, ArchiveDefs, FinaleTexts;
  * sign-строки чертежей data/hypersomnia/lab/*.txt (таблички лаборатории);
  * RoundsBuilder.MARKS (таблички капсул 01..46);
  * прочие запасные тексты кода (дневник, вариант Морзе, кровать, правило ночного кода).

Запуск: gradlew -I src/devtest/lang.init.gradle dumpEnUs && python src/devtest/merge_en_us.py
Существующие ключи и их порядок не меняются; новые добавляются в конец.
"""
import base64
import glob
import io
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
LANG = os.path.join(ROOT, "src", "main", "resources", "assets", "hypersomnia", "lang")
TSV = os.path.join(ROOT, "build", "en_us_fallback.tsv")
BLUEPRINTS = os.path.join(ROOT, "src", "main", "resources", "data", "hypersomnia", "lab", "*.txt")

MARKS = [
    "Cognitive collapse", "Sleep did not resume", "Subject unresponsive",
    "Terminated during session", "Dissociation, discharged",
]

CAPSULE_COUNT = 46

EXTRA_LAB_SIGNS = {
    "session_47_incident_protocol": "SESSION 47|INCIDENT PROTOCOL",
    "console_attempt_response": "CONSOLE|ATTEMPT RESPONSE",
    "intercom": "INTERCOM",
    "session_room": "SESSION ROOM",
    "round_check_hold_position": "ROUND CHECK|HOLD POSITION",
    "roof_ant_1_authorised_staff_only": "ROOF|ANT-1|AUTHORISED|STAFF ONLY",
    # Ключ остался от старой раскладки B4; в коде такой таблички больше нет.
    "lift_access_b4_to_level_b5_director_s_code": "LIFT ACCESS B4|TO LEVEL B5|DIRECTOR'S CODE",
}

EXTRA = {
    "hypersomnia.diary.title": "DIARY",
    "hypersomnia.diary.chronology": "CHRONOLOGY",
    "hypersomnia.diary.pocket": "POCKET",
    "hypersomnia.diary.hint": "CLICK: READ   DRAG: PLACE   CORNER / ARROWS: TURN",
    "hypersomnia.scene.bed": "I can't move.",
    "hypersomnia.archive.g_keypad.1.v1":
        "The code is set automatically to the time of the first gate entry after 20:00, "
        "when the night shift starts: four digits, HHMM.",
    "hypersomnia.archive.g_keypad.1.v2":
        "The code is set automatically to the time the operator on duty passes the gate: "
        "four digits, HHMM.",
    "hypersomnia.archive.g_keypad.1.v3":
        "The code is set automatically to the time of the last gate entry before midnight: "
        "four digits, HHMM.",
}
for _i, _word in enumerate(["HELP", "WAKE", "LATE", "STAY"]):
    EXTRA["hypersomnia.variant.morse.%d" % _i] = _word


def slug(text):
    return re.sub(r"[^a-z0-9]+", "_", text.lower()).strip("_")


def load_generated():
    out = {}
    if not os.path.exists(TSV):
        sys.exit("no %s: run gradlew -I src/devtest/lang.init.gradle dumpEnUs first" % TSV)
    with io.open(TSV, encoding="utf-8") as f:
        for line in f:
            key, b64 = line.rstrip("\n").split("\t", 1)
            out[key] = base64.b64decode(b64).decode("utf-8")
    return out


def load_lab_signs():
    out = {}
    for path in sorted(glob.glob(BLUEPRINTS)):
        with io.open(path, encoding="utf-8") as f:
            for line in f:
                m = re.match(r"\s*sign\s+\S+\s*=\s*(.*)$", line)
                if not m:
                    continue
                parts = m.group(1).split("|")
                while parts and not parts[-1].strip():
                    parts.pop()
                if not parts:
                    continue
                out.setdefault("hypersomnia.lab.sign." + slug(" ".join(parts)), "|".join(parts))
    for i in range(CAPSULE_COUNT):
        mark = MARKS[i % len(MARKS)]
        out["hypersomnia.lab.sign.capsule_%02d_%s" % (i + 1, slug(mark))] = \
            "CAPSULE %02d|%s" % (i + 1, mark)
    for key, value in EXTRA_LAB_SIGNS.items():
        out["hypersomnia.lab.sign." + key] = value
    return out


def main():
    ru_path = os.path.join(LANG, "ru_ru.json")
    en_path = os.path.join(LANG, "en_us.json")
    with io.open(ru_path, encoding="utf-8") as f:
        ru = json.load(f, object_pairs_hook=dict)
    with io.open(en_path, encoding="utf-8") as f:
        en = json.load(f, object_pairs_hook=dict)

    available = {}
    for source in (load_generated(), load_lab_signs(), EXTRA):
        available.update(source)

    missing = [k for k in ru if k not in en]
    absent = [k for k in missing if k not in available]
    if absent:
        sys.exit("no English fallback for %d keys:\n%s" % (len(absent), "\n".join(absent)))

    for key in missing:
        en[key] = available[key]

    with io.open(en_path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(en, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print("en_us.json: %d keys (%d added)" % (len(en), len(missing)))


if __name__ == "__main__":
    main()
