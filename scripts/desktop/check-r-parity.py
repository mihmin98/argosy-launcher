import re
import struct
import sys
import zipfile
from pathlib import Path

RES_STRING_POOL = 0x0001
RES_TABLE = 0x0002
RES_TABLE_PACKAGE = 0x0200
RES_TABLE_TYPE = 0x0201
UTF8_FLAG = 1 << 8
FLAG_COMPLEX = 0x0001
FLAG_COMPACT = 0x0008
TYPE_FLAG_SPARSE = 0x01
TYPE_FLAG_OFFSET16 = 0x02
NO_ENTRY = 0xFFFFFFFF
TYPE_STRING = 0x03
QUANTITY_ATTRS = {
    0x01000004: "other", 0x01000005: "zero", 0x01000006: "one",
    0x01000007: "two", 0x01000008: "few", 0x01000009: "many",
}
QUANTITIES = ["zero", "one", "two", "few", "many", "other"]
PSEUDOLOCALES = {"en-XA", "ar-XB"}
R_CONST = re.compile(r"^\s*const val `?(\w+)`?: Int = 0x([0-9a-f]{8})$")
R_OBJECT = re.compile(r"^    object (\w+) \{$")
OVERRIDE = re.compile(r"<(string|plurals)\s+name=\"(\w+)\"")


def u16(data, at):
    return struct.unpack_from("<H", data, at)[0]


def u32(data, at):
    return struct.unpack_from("<I", data, at)[0]


def string_pool(data, start):
    header_size, size = u16(data, start + 2), u32(data, start + 4)
    count, _, flags, strings_start = struct.unpack_from("<IIII", data, start + 8)
    utf8 = flags & UTF8_FLAG
    pool = []
    for i in range(count):
        at = start + strings_start + u32(data, start + header_size + 4 * i)
        if utf8:
            at += 2 if data[at] & 0x80 else 1
            length = data[at]
            if length & 0x80:
                length = ((length & 0x7F) << 8) | data[at + 1]
                at += 1
            at += 1
            pool.append(data[at:at + length].decode("utf-8"))
        else:
            length = u16(data, at)
            at += 2
            if length & 0x8000:
                length = ((length & 0x7FFF) << 16) | u16(data, at)
                at += 2
            pool.append(data[at:at + 2 * length].decode("utf-16-le"))
    return pool, start + size


def config_tag(data, at):
    language = data[at + 8:at + 10]
    country = data[at + 10:at + 12]
    script = data[at + 36:at + 40]
    if language[0] & 0x80:
        raise SystemExit("check-r-parity: packed three-letter languages are not supported")
    parts = [p.rstrip(b"\x00").decode("ascii") for p in (language, script, country)]
    return "-".join(p for p in parts if p)


def type_entries(data, start, header_size):
    type_flags = data[start + 9]
    entry_count, entries_start = u32(data, start + 12), u32(data, start + 16)
    offsets_at = start + header_size
    if type_flags & TYPE_FLAG_SPARSE:
        for i in range(entry_count):
            index, offset = struct.unpack_from("<HH", data, offsets_at + 4 * i)
            yield index, start + entries_start + 4 * offset
    elif type_flags & TYPE_FLAG_OFFSET16:
        for index in range(entry_count):
            offset = u16(data, offsets_at + 2 * index)
            if offset != 0xFFFF:
                yield index, start + entries_start + 4 * offset
    else:
        for index in range(entry_count):
            offset = u32(data, offsets_at + 4 * index)
            if offset != NO_ENTRY:
                yield index, start + entries_start + offset


def read_table(arsc):
    if u16(arsc, 0) != RES_TABLE:
        raise SystemExit("check-r-parity: not a resource table")
    at = u16(arsc, 2)
    values, end = None, len(arsc)
    result = {}
    while at < end:
        chunk_type, header_size, size = u16(arsc, at), u16(arsc, at + 2), u32(arsc, at + 4)
        if chunk_type == RES_STRING_POOL and values is None:
            values, _ = string_pool(arsc, at)
        elif chunk_type == RES_TABLE_PACKAGE:
            read_package(arsc, at, header_size, size, values, result)
        at += size
    return result


def read_package(data, start, header_size, size, values, result):
    type_names, _ = string_pool(data, start + u32(data, start + 268))
    key_names, _ = string_pool(data, start + u32(data, start + 276))
    at, end = start + header_size, start + size
    while at < end:
        chunk_type, chunk_header, chunk_size = u16(data, at), u16(data, at + 2), u32(data, at + 4)
        if chunk_type == RES_TABLE_TYPE:
            type_name = type_names[data[at + 8] - 1]
            if type_name in ("string", "plurals"):
                tag = config_tag(data, at + 20)
                for _, entry_at in type_entries(data, at, chunk_header):
                    flags = u16(data, entry_at + 2)
                    if flags & FLAG_COMPACT:
                        raise SystemExit("check-r-parity: compact entries are not supported")
                    name = key_names[u32(data, entry_at + 4)]
                    value = read_value(data, entry_at, flags, values)
                    result.setdefault((type_name, name), {})[tag] = value
        at += chunk_size


def read_value(data, entry_at, flags, values):
    entry_size = u16(data, entry_at)
    if not flags & FLAG_COMPLEX:
        data_type, value = data[entry_at + entry_size + 3], u32(data, entry_at + entry_size + 4)
        return values[value] if data_type == TYPE_STRING else ("<non-string>", data_type, value)
    count = u32(data, entry_at + 12)
    forms = {}
    for i in range(count):
        map_at = entry_at + entry_size + 12 * i
        attr, data_type, value = u32(data, map_at), data[map_at + 7], u32(data, map_at + 8)
        forms[QUANTITY_ATTRS.get(attr, hex(attr))] = values[value] if data_type == TYPE_STRING else ("<non-string>", data_type)
    return forms


def read_generated_r(r_file):
    names, kind = {}, None
    for line in r_file.read_text(encoding="utf-8").splitlines():
        opened = R_OBJECT.match(line)
        if opened:
            kind = opened.group(1)
            continue
        constant = R_CONST.match(line)
        if constant:
            names[int(constant.group(2), 16)] = (kind, constant.group(1))
    return names


def read_generated_table(path):
    data = path.read_bytes()
    if data[:4] != b"ARGS":
        raise SystemExit(f"check-r-parity: {path}: bad magic")
    _, count = struct.unpack_from(">ii", data, 4)
    at, entries = 12, {}

    def text():
        nonlocal at
        length = struct.unpack_from(">i", data, at)[0]
        at += 4 + length
        return data[at - length:at].decode("utf-8")

    for _ in range(count):
        entry_id, kind = struct.unpack_from(">ib", data, at)
        at += 5
        if kind == 0:
            entries[entry_id] = text()
        else:
            forms = {}
            count_forms = data[at]
            at += 1
            for _ in range(count_forms):
                quantity = QUANTITIES[data[at]]
                at += 1
                forms[quantity] = text()
            entries[entry_id] = forms
    return entries


def main():
    if len(sys.argv) != 5:
        sys.exit(
            "usage: check-r-parity.py <linked .ap_> <its build type's res dir> "
            "<generated R.kt> <generated strings dir>"
        )
    ap, build_type_res, r_file, tables_dir = (Path(a) for a in sys.argv[1:])
    overridden = {
        (match.group(1), match.group(2))
        for source in build_type_res.glob("values*/*.xml")
        for match in OVERRIDE.finditer(source.read_text(encoding="utf-8"))
    }
    with zipfile.ZipFile(ap) as archive:
        linked = read_table(archive.read("resources.arsc"))
    generated_names = read_generated_r(r_file)
    tables = {
        ("" if path.stem == "base" else path.stem): read_generated_table(path)
        for path in sorted(tables_dir.glob("*.bin"))
    }
    problems = []
    checked = 0
    for entry_id, (kind, name) in sorted(generated_names.items()):
        if kind not in ("string", "plurals") or (kind, name) in overridden:
            continue
        aapt = linked.get((kind, name))
        if aapt is None:
            problems.append(f"{kind}/{name}: not in the linked table")
            continue
        aapt_configs = set(aapt) - PSEUDOLOCALES
        ours = {tag for tag, table in tables.items() if entry_id in table}
        for tag in sorted(aapt_configs ^ ours):
            problems.append(f"{kind}/{name}: locale {tag or 'base'} present in {'aapt2' if tag in aapt_configs else 'desktop'} only")
        for tag in sorted(aapt_configs & ours):
            checked += 1
            if aapt[tag] != tables[tag][entry_id]:
                problems.append(f"{kind}/{name} [{tag or 'base'}]: aapt2 {aapt[tag]!r} != desktop {tables[tag][entry_id]!r}")
    for problem in problems[:60]:
        print(problem)
    skipped = ", ".join(f"{kind}/{name}" for kind, name in sorted(overridden))
    print(f"check-r-parity: {checked} values compared, {len(problems)} differences; skipped {skipped or 'none'} ({build_type_res})")
    sys.exit(1 if problems else 0)


if __name__ == "__main__":
    main()
