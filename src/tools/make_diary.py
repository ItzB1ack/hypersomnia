"""Diary: 3D closed book + two cover-opening frames.

Generation lives in make_3d_items.py; the old 2D icon is no longer used by the
item model.

Run: python src/tools/make_diary.py
"""
from make_3d_items import diary

if __name__ == "__main__":
    diary()
    print("diary: 3d book")
