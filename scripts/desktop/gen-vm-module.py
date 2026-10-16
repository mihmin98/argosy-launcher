import re
import sys
from pathlib import Path
from typing import NoReturn

PACKAGE = "com.nendo.argosy.desktop.di"
PACKAGE_LINE = re.compile(r"^package ([\w.]+)\s*$")
VIEW_MODEL = {"@HiltViewModel", "@dagger.hilt.android.lifecycle.HiltViewModel"}
ENTRY_POINT = {"@EntryPoint", "@dagger.hilt.EntryPoint"}
INSTALL_IN = re.compile(r"^@(?:dagger\.hilt\.)?InstallIn\((?:dagger\.hilt\.components\.)?(\w+)::class\)$")
MODIFIERS = r"(?:(?:public|internal|private|open|final|abstract)\s+)*"
CLASS = re.compile(rf"^({MODIFIERS})class\s+(\w+)\b")
INTERFACE = re.compile(rf"^({MODIFIERS})interface\s+(\w+)\b")


def fail(source, line_no, message) -> NoReturn:
    sys.exit(f"gen-vm-module: {source}:{line_no}: {message}")


def declaration(source, lines, start, pattern, kind):
    annotations = []
    for no in range(start, len(lines)):
        raw = lines[no]
        line = raw.strip()
        if not line or line.startswith(("//", "/*", "*")):
            continue
        if line.startswith("@"):
            annotations.append(line)
            continue
        if raw[0].isspace():
            fail(source, no + 1, f"nested {kind} `{line}` is not supported")
        match = pattern.match(line)
        if not match:
            fail(source, no + 1, f"expected a top-level {kind}, found `{line}`")
        if "private" in match.group(1).split():
            fail(source, no + 1, f"private {kind} `{match.group(2)}` cannot be bound")
        return match.group(2), annotations
    fail(source, start, f"annotation without a {kind}")


def collect(sources):
    view_models = []
    entry_points = []
    for source in sources:
        lines = source.read_text(encoding="utf-8").splitlines()
        package = next((m.group(1) for m in map(PACKAGE_LINE.match, lines) if m), None)
        for no, raw in enumerate(lines):
            line = raw.strip()
            if line.startswith(("@HiltViewModel(", "@dagger.hilt.android.lifecycle.HiltViewModel(")):
                fail(source, no + 1, "@HiltViewModel arguments (assisted injection) are not supported")
            is_view_model = line in VIEW_MODEL
            is_entry_point = line in ENTRY_POINT
            if not (is_view_model or is_entry_point):
                continue
            if raw[0].isspace():
                fail(source, no + 1, f"indented `{line}` is not supported")
            if package is None:
                fail(source, no + 1, "file has no package")
            if is_view_model:
                name, _ = declaration(source, lines, no + 1, CLASS, "class")
                view_models.append(f"{package}.{name}")
                continue
            name, annotations = declaration(source, lines, no + 1, INTERFACE, "interface")
            components = [m.group(1) for m in map(INSTALL_IN.match, annotations) if m]
            if components != ["SingletonComponent"]:
                fail(source, no + 1, f"entry point `{name}` must be @InstallIn(SingletonComponent::class)")
            entry_points.append(f"{package}.{name}")
    return sorted(set(view_models)), sorted(set(entry_points))


def binding_name(fqn):
    simple = fqn.rsplit(".", 1)[-1]
    return simple[0].lower() + simple[1:]


def render_module(view_models):
    names = [binding_name(fqn) for fqn in view_models]
    duplicates = sorted({name for name in names if names.count(name) > 1})
    if duplicates:
        sys.exit(f"gen-vm-module: view models share a simple name: {', '.join(duplicates)}")
    lines = [
        f"package {PACKAGE}",
        "",
        "import androidx.lifecycle.ViewModel",
        "import dagger.Binds",
        "import dagger.Module",
        "import dagger.multibindings.ClassKey",
        "import dagger.multibindings.IntoMap",
        "import dagger.multibindings.Multibinds",
        "",
        "@Module",
        "abstract class DesktopViewModelModule {",
        "",
        "    @Multibinds",
        "    abstract fun viewModels(): Map<Class<*>, ViewModel>",
    ]
    for fqn, name in zip(view_models, names):
        lines += [
            "",
            "    @Binds",
            "    @IntoMap",
            f"    @ClassKey({fqn}::class)",
            f"    abstract fun {name}(viewModel: {fqn}): ViewModel",
        ]
    lines.append("}")
    return "\n".join(lines) + "\n"


def render_entry_points(entry_points):
    supertypes = "".join(f"\n    {fqn}," for fqn in entry_points)
    header = f"interface DesktopEntryPoints :{supertypes.rstrip(',')}" if entry_points else "interface DesktopEntryPoints"
    return f"package {PACKAGE}\n\n{header}\n"


def main():
    if len(sys.argv) != 3:
        sys.exit("usage: gen-vm-module.py <file listing compiled sources> <output source root>")
    listing, root = Path(sys.argv[1]), Path(sys.argv[2])
    sources = [Path(p) for p in listing.read_text(encoding="utf-8").splitlines() if p]
    view_models, entry_points = collect(sources)
    target = root / PACKAGE.replace(".", "/")
    target.mkdir(parents=True, exist_ok=True)
    (target / "DesktopViewModelModule.kt").write_text(render_module(view_models), encoding="utf-8")
    (target / "DesktopEntryPoints.kt").write_text(render_entry_points(entry_points), encoding="utf-8")
    print(f"gen-vm-module: {len(view_models)} view models, {len(entry_points)} entry points -> {target}")


if __name__ == "__main__":
    main()
