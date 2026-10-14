import org.tomlj.Toml
import org.tomlj.TomlArray
import org.tomlj.TomlParseResult

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath(
            project.extensions.getByType<VersionCatalogsExtension>()
                .named("desktopLibs").findLibrary("tomlj").get()
        )
    }
}

fun parseToml(name: String): TomlParseResult? {
    val source = file(name)
    if (!source.exists()) return null
    val result = Toml.parse(source.toPath())
    if (result.hasErrors()) {
        throw GradleException("desktopApp/$name: ${result.errors().joinToString("; ")}")
    }
    return result
}

fun TomlArray?.strings(): List<String> =
    this?.let { array -> (0 until array.size()).map { array.getString(it) } }.orEmpty()

val manifest = parseToml("overlay.toml")
val shadows = parseToml("shadows.toml")

val excludedDirs = manifest?.getArray("exclude.dirs").strings().toSet()
val includedBack = manifest?.getArray("include_back.files").strings().toSet()
val replacedFiles = shadows?.getArray("shadow")?.let { entries ->
    (0 until entries.size()).map { entries.getTable(it).getString("upstream")!! }
}.orEmpty()
val excludedFiles = (manifest?.getArray("exclude.files").strings().orEmpty() + replacedFiles).toSet()
val onlyDirs = manifest?.getArray("only.dirs").strings().toSet()
val onlyFiles = manifest?.getArray("only.files").strings().toSet()
val narrowed = onlyDirs.isNotEmpty() || onlyFiles.isNotEmpty()

fun outsideOnly(path: String): Boolean = narrowed &&
    path !in onlyFiles &&
    onlyDirs.none { path == it || path.startsWith("$it/") } &&
    (onlyDirs + onlyFiles).none { it.startsWith("$path/") }

val isExcluded: (String) -> Boolean = { path ->
    when {
        outsideOnly(path) -> true
        path in includedBack -> false
        includedBack.any { it.startsWith("$path/") } -> false
        path in excludedFiles -> true
        else -> excludedDirs.any { path == it || path.startsWith("$it/") }
    }
}

val upstreamRoot = rootProject.file("app/src/main/kotlin")

extra["overlay.isExcluded"] = isExcluded
extra["overlay.upstreamRoot"] = upstreamRoot

tasks.register("overlayReport") {
    group = "desktop"
    description = "Counts the upstream Kotlin files the desktop overlay compiles and excludes."
    doLast {
        val sources = upstreamRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { it.relativeTo(upstreamRoot).invariantSeparatorsPath }
            .toList()
        val excluded = sources.filter(isExcluded)
        println("upstream .kt files: ${sources.size}")
        println("compiled:           ${sources.size - excluded.size}")
        println("excluded:           ${excluded.size}")
        if (narrowed) println("narrowed by [only]: ${onlyDirs.size} dirs, ${onlyFiles.size} files")
        val unmatched = (excludedDirs + onlyDirs).filter { dir -> sources.none { it.startsWith("$dir/") } } +
            (excludedFiles + includedBack + onlyFiles).filter { it !in sources }
        if (unmatched.isEmpty()) {
            println("every overlay entry matches upstream")
        } else {
            println("entries matching no upstream file:")
            unmatched.forEach { println("  $it") }
        }
    }
}
