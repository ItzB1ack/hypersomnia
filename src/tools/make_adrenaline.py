"""Adrenaline syringe: 3D cuboid model + frames driven by hypersomnia:using.

The actual generation lives in make_3d_items.py so the flat 16x16 icon and the
3D model cannot drift apart.

Run: python src/tools/make_adrenaline.py
"""
from make_3d_items import syringe

if __name__ == "__main__":
    syringe()
    print("adrenaline: 3d frames")
