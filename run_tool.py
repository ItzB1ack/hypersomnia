"""Запуск скриптов из src/tools из корня проекта.
python run_tool.py              - пронумерованный список
python run_tool.py 2            - запустить по номеру
python run_tool.py make_heart   - запустить по имени (.py необязательно)
python run_tool.py 0            - запустить все (или all)
Остальные аргументы передаются скрипту."""
import os
import subprocess
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))
TOOLS = os.path.join(ROOT, "src", "tools")


def scripts():
    return sorted(f for f in os.listdir(TOOLS) if f.endswith(".py"))


def run(name, args):
    path = os.path.join(TOOLS, name)
    print("== " + name)
    return subprocess.call([sys.executable, path] + args, cwd=ROOT)


def show():
    print("Scripts in src/tools:")
    print("  0. all")
    for i, s in enumerate(scripts(), start=1):
        print("  %d. %s" % (i, s[:-3]))


def main():
    if len(sys.argv) < 2:
        show()
        return 0
    target, args = sys.argv[1], sys.argv[2:]
    items = scripts()
    if target == "0" or target == "all":
        code = 0
        for s in items:
            code = run(s, args) or code
        return code
    if target.isdigit():
        n = int(target)
        if 1 <= n <= len(items):
            return run(items[n - 1], args)
        print("No script with number " + target)
        return 1
    name = target if target.endswith(".py") else target + ".py"
    if name not in items:
        print("No such script: " + name)
        return 1
    return run(name, args)


if __name__ == "__main__":
    sys.exit(main())