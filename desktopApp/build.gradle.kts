import org.jetbrains.kotlin.compose.compiler.gradle.ComposeFeatureFlag

plugins {
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.kotlin.compose)
    alias(desktopLibs.plugins.compose.multiplatform)
    id("com.google.devtools.ksp")
}

apply(from = "overlay.gradle.kts")

@Suppress("UNCHECKED_CAST")
val isExcludedUpstream = extra["overlay.isExcluded"] as (String) -> Boolean
val upstreamRoot = extra["overlay.upstreamRoot"] as File

kotlin {
    jvmToolchain(21)
}

val generateDesktopMigrations by tasks.registering(Exec::class) {
    val source = File(upstreamRoot, "com/nendo/argosy/data/local/migrations/Migrations.kt")
    val script = rootProject.file("scripts/desktop/gen-migrations.py")
    val outputDir = layout.buildDirectory.dir("generated/desktop/migrations")
    inputs.files(source, script)
    outputs.dir(outputDir)
    commandLine(
        "python3", script.path, source.path,
        outputDir.get().file("com/nendo/argosy/data/local/migrations/Migrations.kt").asFile.path,
    )
}

val generateDesktopDaoModule by tasks.registering(Exec::class) {
    val source = File(upstreamRoot, "com/nendo/argosy/data/local/ALauncherDatabase.kt")
    val script = rootProject.file("scripts/desktop/gen-dao-module.py")
    val outputDir = layout.buildDirectory.dir("generated/desktop/daomodule")
    inputs.files(source, script)
    outputs.dir(outputDir)
    commandLine(
        "python3", script.path, source.path,
        outputDir.get().file("com/nendo/argosy/desktop/di/DesktopDaoModule.kt").asFile.path,
    )
}

val overlayExclusion = Spec<FileTreeElement> { element ->
    element.file.startsWith(upstreamRoot) && isExcludedUpstream(element.relativePath.pathString)
}

val generateDesktopViewModelModule by tasks.registering(Exec::class) {
    val sources = files(
        fileTree(upstreamRoot) { include("**/*.kt"); exclude(overlayExclusion) },
        fileTree("src/main/kotlin") { include("**/*.kt") },
    )
    val script = rootProject.file("scripts/desktop/gen-vm-module.py")
    val listing = temporaryDir.resolve("sources.txt")
    val outputDir = layout.buildDirectory.dir("generated/desktop/vmmodule")
    inputs.files(sources, script)
    outputs.dir(outputDir)
    doFirst {
        listing.writeText(sources.files.sorted().joinToString("\n", postfix = "\n") { it.path })
    }
    commandLine("python3", script.path, listing.path, outputDir.get().asFile.path)
}

val upstreamRes = rootProject.file("app/src/main/res")
val generatedResources = layout.buildDirectory.dir("generated/desktop/res")

val generateDesktopResources by tasks.registering(Exec::class) {
    val script = rootProject.file("scripts/desktop/gen-r.py")
    inputs.files(fileTree(upstreamRes) { include("values*/**", "drawable*/**", "raw*/**") }, script)
    outputs.dir(generatedResources)
    commandLine(
        "python3", script.path, upstreamRes.path,
        generatedResources.get().dir("kotlin").asFile.path,
        generatedResources.get().dir("resources").asFile.path,
    )
}

sourceSets.main {
    resources.srcDir(files(generatedResources.map { it.dir("resources") }).builtBy(generateDesktopResources))
    kotlin.srcDir(files(generatedResources.map { it.dir("kotlin") }).builtBy(generateDesktopResources))
    kotlin.srcDir(upstreamRoot)
    kotlin.srcDir("src/main/kotlin")
    kotlin.srcDir(generateDesktopMigrations)
    kotlin.srcDir(generateDesktopDaoModule)
    kotlin.srcDir(generateDesktopViewModelModule)
    kotlin.exclude(overlayExclusion)
}

tasks.processResources {
    from(upstreamRes) {
        include("drawable/*.xml", "raw/*")
        into("argosy/res")
    }
}

tasks.matching { it.name.startsWith("ksp") && it is PatternFilterable }.configureEach {
    (this as PatternFilterable).exclude(overlayExclusion)
}

composeCompiler {
    stabilityConfigurationFiles.add(
        rootProject.layout.projectDirectory.file("app/compose_stability_config.conf")
    )
    featureFlags.add(ComposeFeatureFlag.IntrinsicRemember.disabled())
}

ksp {
    arg("room.schemaLocation", layout.buildDirectory.dir("room-schemas").get().asFile.path)
}

dependencies {
    implementation(compose.runtime)
    implementation(compose.ui)
    implementation(libs.room.runtime)
    implementation(desktopLibs.sqlite.bundled)
    implementation(desktopLibs.kotlinx.coroutines.core)
    implementation(desktopLibs.kotlinx.coroutines.swing)
    implementation(desktopLibs.datastore.preferences.core)
    implementation(desktopLibs.dagger)
    implementation(desktopLibs.hilt.core)
    implementation(desktopLibs.jetbrains.lifecycle.viewmodel.compose)
    implementation(desktopLibs.jetbrains.lifecycle.viewmodel.savedstate)
    implementation(desktopLibs.icu4j)

    testImplementation(libs.junit)
    testImplementation(desktopLibs.json)
    testImplementation(compose.desktop.currentOs)
    testImplementation(desktopLibs.jetbrains.lifecycle.runtime.compose)
    @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
    testImplementation(compose.desktop.uiTestJUnit4)

    ksp(libs.room.compiler)
    ksp(libs.moshi.kotlin)
    ksp(desktopLibs.dagger.compiler)
    kspTest(desktopLibs.dagger.compiler)
}

val roomSchemaDir = "com.nendo.argosy.data.local.ALauncherDatabase"

tasks.test {
    val upstreamSchemas = rootProject.file("app/schemas/$roomSchemaDir")
    inputs.dir(rootProject.file("app/schemas"))
    systemProperty("argosy.upstreamSchemas", upstreamSchemas.path)
    systemProperty("argosy.schemaRoot", rootProject.file("app/schemas").path)
    systemProperty("argosy.upstreamRoot", upstreamRoot.path)
    systemProperty("argosy.desktopSchemas", layout.buildDirectory.dir("room-schemas/$roomSchemaDir").get().asFile.path)
}

compose.desktop {
    application {
        mainClass = "com.nendo.argosy.desktop.MainKt"
    }
}
