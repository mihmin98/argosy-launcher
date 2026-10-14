import re
import sys
from pathlib import Path

IMPORT_REWRITES = {
    "import androidx.room.migration.Migration": "import com.nendo.argosy.desktop.db.LegacyMigration",
    "import androidx.sqlite.db.SupportSQLiteDatabase": "import com.nendo.argosy.desktop.db.SupportSQLiteDatabase",
}
HEADER = re.compile(r"^object Migration_(\d+)_(\d+) : Migration\((\d+), (\d+)\) \{$")
MIGRATE = "    override fun migrate(db: SupportSQLiteDatabase) {"


def fail(source, line_no, message):
    sys.exit(f"gen-migrations: {source}:{line_no}: {message}")


def rewrite(source):
    lines = source.read_text(encoding="utf-8").splitlines()
    seen_imports = set()
    headers = 0
    migrates = 0
    out = []
    for no, line in enumerate(lines, start=1):
        stripped = line.strip()
        if stripped.startswith("import "):
            if stripped in IMPORT_REWRITES:
                seen_imports.add(stripped)
                out.append(IMPORT_REWRITES[stripped])
                continue
            if stripped.startswith(("import android.", "import androidx.")):
                fail(source, no, f"no desktop rewrite for `{stripped}`")
        header = HEADER.match(line)
        if header:
            names = header.group(1, 2)
            args = header.group(3, 4)
            if names != args:
                fail(source, no, f"Migration_{names[0]}_{names[1]} declares Migration({args[0]}, {args[1]})")
            headers += 1
            out.append(line.replace(": Migration(", ": LegacyMigration(", 1))
            continue
        if re.search(r"\bMigration\b", line) and not re.search(r"\bMigration_\d+_\d+\b", line):
            fail(source, no, f"unrecognised Migration construct: `{stripped}`")
        if line == MIGRATE:
            migrates += 1
        out.append(line)
    missing = set(IMPORT_REWRITES) - seen_imports
    if missing:
        fail(source, 0, f"expected imports not found: {sorted(missing)}")
    if headers == 0 or headers != migrates:
        fail(source, 0, f"{headers} migration objects but {migrates} `migrate(db)` overrides")
    return "\n".join(out) + "\n", headers


def main():
    if len(sys.argv) != 3:
        sys.exit("usage: gen-migrations.py <upstream Migrations.kt> <output Migrations.kt>")
    source, target = Path(sys.argv[1]), Path(sys.argv[2])
    text, count = rewrite(source)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text, encoding="utf-8")
    print(f"gen-migrations: {count} migrations -> {target}")


if __name__ == "__main__":
    main()
