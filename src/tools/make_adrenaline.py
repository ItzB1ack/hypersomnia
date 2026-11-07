"""Иконки адреналинового шприца: базовый кадр + три кадра применения.

Кадры подключаются через предметное свойство hypersomnia:using
(0..1 = прогресс удержания ПКМ) и overrides в модели предмета.
Запуск: python src/tools/make_adrenaline.py (нужен Pillow).
"""
import json
import os
from PIL import Image

from make_documents import OUT, MODELS, draw_syringe

FLUID = (92, 150, 166, 255)
FLUID_HI = (170, 216, 224, 255)
LABEL = (150, 34, 48, 255)

FRAMES = [
    ("adrenaline_syringe", 0.00),
    ("adrenaline_syringe_use_0", 0.08),
    ("adrenaline_syringe_use_1", 0.50),
    ("adrenaline_syringe_use_2", 0.95),
]


def model(name, overrides=None):
    data = {"parent": "minecraft:item/generated",
            "textures": {"layer0": "hypersomnia:item/%s" % name}}
    if overrides:
        data["overrides"] = overrides
    with open(os.path.join(MODELS, name + ".json"), "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def main():
    for name, push in FRAMES:
        draw_syringe(FLUID, FLUID_HI, LABEL, push=push).save(
            os.path.join(OUT, name + ".png"))
        model(name, [
            {"predicate": {"hypersomnia:using": 0.001},
             "model": "hypersomnia:item/adrenaline_syringe_use_0"},
            {"predicate": {"hypersomnia:using": 0.45},
             "model": "hypersomnia:item/adrenaline_syringe_use_1"},
            {"predicate": {"hypersomnia:using": 0.80},
             "model": "hypersomnia:item/adrenaline_syringe_use_2"},
        ] if name == "adrenaline_syringe" else None)
    print("adrenaline: %d frames" % len(FRAMES))


if __name__ == "__main__":
    main()
