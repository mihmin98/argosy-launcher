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

sourceSets.main {
    kotlin.srcDir(upstreamRoot)
    kotlin.srcDir("src/main/kotlin")
    kotlin.srcDir(layout.buildDirectory.dir("generated/desktop/kotlin"))
    kotlin.exclude { element ->
        element.file.startsWith(upstreamRoot) && isExcludedUpstream(element.relativePath.pathString)
    }
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
    implementation(libs.room.runtime)
    implementation(desktopLibs.sqlite.bundled)
    implementation(desktopLibs.kotlinx.coroutines.core)

    testImplementation(libs.junit)

    ksp(libs.room.compiler)
    ksp(libs.moshi.kotlin)
    ksp(desktopLibs.dagger.compiler)
}

val roomSchemaDir = "com.nendo.argosy.data.local.ALauncherDatabase"

tasks.test {
    val upstreamSchemas = rootProject.file("app/schemas/$roomSchemaDir")
    inputs.dir(upstreamSchemas)
    systemProperty("argosy.upstreamSchemas", upstreamSchemas.path)
    systemProperty("argosy.desktopSchemas", layout.buildDirectory.dir("room-schemas/$roomSchemaDir").get().asFile.path)
}

compose.desktop {
    application {
        mainClass = "com.nendo.argosy.desktop.MainKt"
    }
}
