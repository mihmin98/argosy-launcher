import struct
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import NoReturn

R_PACKAGE = "com.nendo.argosy"
INDEX_PACKAGE = "com.nendo.argosy.desktop.res"
TYPES = ["drawable", "plurals", "raw", "string"]
QUANTITIES = ["zero", "one", "two", "few", "many", "other"]
IGNORED_VALUES = {"color", "dimen", "bool", "integer", "style", "attr", "declare-styleable", "eat-comment"}
XML_SPACE = " \t\n\v\f\r"
KOTLIN_HARD_KEYWORDS = {
    "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface",
    "is", "null", "object", "package", "return", "super", "this", "throw", "true", "try", "typealias",
    "typeof", "val", "var", "when", "while",
}
TABLE_MAGIC = b"ARGS"
TABLE_VERSION = 1


def fail(where, message) -> NoReturn:
    sys.exit(f"gen-r: {where}: {message}")


def locale_tag(values_dir):
    name = values_dir.name
    if name == "values":
        return ""
    qualifier = name[len("values-"):]
    if qualifier.startswith("b+"):
        return "-".join(qualifier[2:].split("+"))
    parts = qualifier.split("-")
    if len(parts) == 1 and parts[0].isalpha() and len(parts[0]) in (2, 3):
        return parts[0]
    if len(parts) == 2 and parts[0].isalpha() and parts[1].startswith("r") and len(parts[1]) == 3:
        return f"{parts[0]}-{parts[1][1:]}"
    fail(values_dir, f"unsupported resource qualifier `{qualifier}`")


class TextBuilder:
    def __init__(self, where):
        self.where = where
        self.out = []
        self.quote = False
        self.last_was_space = False

    def append(self, text):
        chars = iter(text)
        for c in chars:
            if not self.quote and c in XML_SPACE:
                if not self.last_was_space:
                    self.out.append((" ", True))
                    self.last_was_space = True
                continue
            self.last_was_space = False
            if c == "\\":
                escaped = next(chars, None)
                if escaped is None:
                    continue
                if escaped == "t":
                    self.out.append(("\t", False))
                elif escaped == "n":
                    self.out.append(("\n", False))
                elif escaped == "u":
                    digits = "".join(next(chars, "") for _ in range(4))
                    if len(digits) != 4 or any(d not in "0123456789abcdefABCDEF" for d in digits):
                        fail(self.where, f"invalid unicode escape `\\u{digits}`")
                    self.out.append((chr(int(digits, 16)), False))
                else:
                    self.out.append((escaped, False))
            elif c == '"':
                self.quote = not self.quote
            elif c == "'" and not self.quote:
                fail(self.where, "unescaped apostrophe")
            else:
                self.out.append((c, False))

    def text(self):
        start, end = 0, len(self.out)
        while start < end and self.out[start][1]:
            start += 1
        while end > start and self.out[end - 1][1]:
            end -= 1
        return "".join(c for c, _ in self.out[start:end])


def flatten(element, where):
    builder = TextBuilder(where)

    def walk(node):
        if node.text:
            builder.append(node.text)
        for child in node:
            walk(child)
            if child.tail:
                builder.append(child.tail)

    walk(element)
    return builder.text()


def parse_values(values_dir):
    strings, plurals = {}, {}
    for source in sorted(values_dir.glob("*.xml")):
        root = ET.parse(source).getroot()
        if root.tag != "resources":
            fail(source, f"root is <{root.tag}>, not <resources>")
        for element in root:
            if not isinstance(element.tag, str):
                continue
            name = element.get("name")
            where = f"{source}:{name}"
            if element.tag == "string":
                if name in strings:
                    fail(where, "duplicate string")
                strings[name] = flatten(element, where)
            elif element.tag == "plurals":
                if name in plurals:
                    fail(where, "duplicate plurals")
                forms = {}
                for item in element:
                    quantity = item.get("quantity")
                    if item.tag != "item" or quantity not in QUANTITIES or quantity in forms:
                        fail(where, f"bad plurals item <{item.tag} quantity={quantity!r}>")
                    forms[quantity] = flatten(item, f"{where}[{quantity}]")
                plurals[name] = forms
            elif element.tag == "item" and element.get("type") in ("string", "plurals"):
                fail(where, f"<item type=\"{element.get('type')}\"> is not supported")
            elif element.tag not in IGNORED_VALUES:
                fail(where, f"unsupported value resource <{element.tag}>")
    return strings, plurals


def file_resources(res, kind):
    files = {}
    directory = res / kind
    for path in sorted(directory.iterdir()) if directory.is_dir() else []:
        name = path.name.split(".", 1)[0]
        if name in files:
            fail(path, f"duplicate {kind} `{name}`")
        files[name] = path.name
    for qualified in res.glob(f"{kind}-*"):
        fail(qualified, f"qualified {kind} directories are not supported")
    return files


def assign_ids(names_by_type):
    ids = {}
    for type_index, kind in enumerate(TYPES, start=1):
        for entry, name in enumerate(sorted(names_by_type[kind])):
            ids[(kind, name)] = 0x7F000000 | (type_index << 16) | entry
    return ids


def kotlin_name(name):
    return f"`{name}`" if name in KOTLIN_HARD_KEYWORDS else name


def render_r(names_by_type, ids):
    lines = [f"package {R_PACKAGE}", "", "object R {"]
    for kind in TYPES:
        lines += ["", f"    object {kind} {{"]
        for name in sorted(names_by_type[kind]):
            lines.append(f"        const val {kotlin_name(name)}: Int = 0x{ids[(kind, name)]:08x}")
        lines.append("    }")
    lines.append("}")
    return "\n".join(lines) + "\n"


def render_index(locales, drawables, raws):
    def quoted(values):
        return ", ".join(f'"{v}"' for v in values)

    drawable_files = [drawables[name] for name in sorted(drawables)]
    raw_files = [raws[name] for name in sorted(raws)]
    return "\n".join([
        f"package {INDEX_PACKAGE}",
        "",
        "object GeneratedResources {",
        "",
        f"    val locales: List<String> = listOf({quoted(locales)})",
        "",
        f"    private val drawableFiles = arrayOf({quoted(drawable_files)})",
        "",
        f"    private val rawFiles = arrayOf({quoted(raw_files)})",
        "",
        f"    fun drawablePath(id: Int): String? = entry(id, {TYPES.index('drawable') + 1}, drawableFiles)?.let {{ \"argosy/res/drawable/$it\" }}",
        "",
        f"    fun rawPath(id: Int): String? = entry(id, {TYPES.index('raw') + 1}, rawFiles)?.let {{ \"argosy/res/raw/$it\" }}",
        "",
        "    private fun entry(id: Int, type: Int, files: Array<String>): String? =",
        "        if (id ushr 16 == (0x7f00 or type)) files.getOrNull(id and 0xffff) else null",
        "}",
    ]) + "\n"


def encode(text):
    data = text.encode("utf-8")
    return struct.pack(">i", len(data)) + data


def render_table(strings, plurals, ids):
    entries = []
    for name, text in strings.items():
        entries.append((ids[("string", name)], b"\x00" + encode(text)))
    for name, forms in plurals.items():
        body = b"\x01" + struct.pack(">b", len(forms))
        for quantity in QUANTITIES:
            if quantity in forms:
                body += struct.pack(">b", QUANTITIES.index(quantity)) + encode(forms[quantity])
        entries.append((ids[("plurals", name)], body))
    entries.sort()
    out = TABLE_MAGIC + struct.pack(">ii", TABLE_VERSION, len(entries))
    for entry_id, body in entries:
        out += struct.pack(">i", entry_id) + body
    return out


def main():
    if len(sys.argv) != 4:
        sys.exit("usage: gen-r.py <app/src/main/res> <kotlin output root> <resources output root>")
    res, kotlin_root, resources_root = (Path(a) for a in sys.argv[1:])
    tables = {}
    for values_dir in sorted(res.glob("values*")):
        tag = locale_tag(values_dir)
        tables[tag] = parse_values(values_dir)
    if "" not in tables:
        fail(res, "no base values directory")
    base_strings, base_plurals = tables[""]
    for tag, (strings, plurals) in tables.items():
        for name in strings.keys() - base_strings.keys():
            fail(f"values-{tag}", f"string `{name}` has no default")
        for name in plurals.keys() - base_plurals.keys():
            fail(f"values-{tag}", f"plurals `{name}` has no default")

    drawables = file_resources(res, "drawable")
    raws = file_resources(res, "raw")
    names_by_type = {"drawable": drawables, "plurals": base_plurals, "raw": raws, "string": base_strings}
    ids = assign_ids(names_by_type)

    r_file = kotlin_root / R_PACKAGE.replace(".", "/") / "R.kt"
    index_file = kotlin_root / INDEX_PACKAGE.replace(".", "/") / "GeneratedResources.kt"
    for path in (r_file, index_file):
        path.parent.mkdir(parents=True, exist_ok=True)
    r_file.write_text(render_r(names_by_type, ids), encoding="utf-8")
    locales = sorted(tag for tag in tables if tag)
    index_file.write_text(render_index(locales, drawables, raws), encoding="utf-8")

    table_dir = resources_root / "argosy" / "strings"
    table_dir.mkdir(parents=True, exist_ok=True)
    for stale in table_dir.glob("*.bin"):
        stale.unlink()
    for tag, (strings, plurals) in tables.items():
        (table_dir / f"{tag or 'base'}.bin").write_bytes(render_table(strings, plurals, ids))
    print(
        f"gen-r: {len(base_strings)} strings, {len(base_plurals)} plurals, {len(drawables)} drawables, "
        f"{len(raws)} raw, locales {', '.join(locales)}"
    )


if __name__ == "__main__":
    main()
