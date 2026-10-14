import re
import sys
from pathlib import Path
from typing import NoReturn

GETTER = re.compile(r"^\s*abstract fun (\w+)\(\): ([\w.]+)\s*$")
IMPORT = re.compile(r"^import ([\w.]+)\s*$")
PACKAGE = "com.nendo.argosy.desktop.di"


def fail(source, line_no, message) -> NoReturn:
    sys.exit(f"gen-dao-module: {source}:{line_no}: {message}")


def collect(source):
    imports = {}
    getters = []
    for no, line in enumerate(source.read_text(encoding="utf-8").splitlines(), start=1):
        imported = IMPORT.match(line)
        if imported:
            fqn = imported.group(1)
            imports[fqn.rsplit(".", 1)[-1]] = fqn
            continue
        if "abstract fun" not in line:
            continue
        getter = GETTER.match(line)
        if not getter:
            fail(source, no, f"unrecognised abstract member: `{line.strip()}`")
        name, type_name = getter.groups()
        fqn = type_name if "." in type_name else imports.get(type_name)
        if fqn is None:
            fail(source, no, f"`{type_name}` is neither imported nor fully qualified")
        if not fqn.endswith("Dao"):
            fail(source, no, f"`{name}()` returns `{fqn}`, not a DAO")
        getters.append((name, fqn))
    if not getters:
        fail(source, 0, "no DAO getters found")
    return getters


def render(getters):
    lines = [
        f"package {PACKAGE}",
        "",
        "import com.nendo.argosy.data.local.ALauncherDatabase",
        "import dagger.Module",
        "import dagger.Provides",
        "",
        "@Module",
        "object DesktopDaoModule {",
    ]
    for name, fqn in sorted(getters):
        simple = fqn.rsplit(".", 1)[-1]
        lines += [
            "",
            "    @Provides",
            f"    fun provide{simple}(database: ALauncherDatabase): {fqn} = database.{name}()",
        ]
    lines.append("}")
    return "\n".join(lines) + "\n"


def main():
    if len(sys.argv) != 3:
        sys.exit("usage: gen-dao-module.py <upstream ALauncherDatabase.kt> <output DesktopDaoModule.kt>")
    source, target = Path(sys.argv[1]), Path(sys.argv[2])
    getters = collect(source)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(render(getters), encoding="utf-8")
    print(f"gen-dao-module: {len(getters)} DAOs -> {target}")


if __name__ == "__main__":
    main()
