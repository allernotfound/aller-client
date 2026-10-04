import net.fabricmc.loom.api.LoomGradleExtensionAPI

plugins {
    java
}

val mc = stonecutter.current.version
// Minecraft 26.1+ ships unobfuscated, so Loom skips remapping entirely there.
val obfuscated = stonecutter.eval(mc, "<26.1")

apply(plugin = if (obfuscated) "net.fabricmc.fabric-loom-remap" else "net.fabricmc.fabric-loom")

fun prop(name: String) = property(name) as String

version = "${prop("mod.version")}+$mc"
group = prop("mod.group")
base.archivesName = prop("mod.id")

// `-Paller.compat` adds Sodium and Iris to the dev runtime, to check Aller renders correctly beside them.
val compat = providers.gradleProperty("aller.compat").isPresent

repositories {
    mavenCentral()
    maven("https://maven.terraformersmc.com/")
    if (compat) {
        exclusiveContent {
            forRepository { maven("https://api.modrinth.com/maven") }
            filter { includeGroup("maven.modrinth") }
        }
    }
}

val loom = extensions.getByType<LoomGradleExtensionAPI>()
val modImpl = if (obfuscated) "modImplementation" else "implementation"

dependencies {
    "minecraft"("com.mojang:minecraft:$mc")
    if (obfuscated) "mappings"(loom.officialMojangMappings())
    modImpl("net.fabricmc:fabric-loader:${prop("deps.fabric_loader")}")
    modImpl("net.fabricmc.fabric-api:fabric-api:${prop("deps.fabric_api")}")
    modImpl("com.terraformersmc:modmenu:${prop("deps.modmenu")}")
    // What the pack store reads Modrinth's pages with: Markdown, the HTML mixed into it, and WebP
    // pictures. Plain Java libraries, nested in the jar; `include` does not follow dependencies,
    // so each one's own are listed.
    for (library in listOf(
        "org.commonmark:commonmark:0.30.0",
        "org.commonmark:commonmark-ext-gfm-tables:0.30.0",
        "org.commonmark:commonmark-ext-gfm-strikethrough:0.30.0",
        "org.commonmark:commonmark-ext-autolink:0.30.0",
        "org.nibor.autolink:autolink:0.12.0",
        "org.jsoup:jsoup:1.23.2",
        "com.twelvemonkeys.imageio:imageio-webp:3.15.2",
        "com.twelvemonkeys.imageio:imageio-core:3.15.2",
        "com.twelvemonkeys.imageio:imageio-metadata:3.15.2",
        "com.twelvemonkeys.common:common-lang:3.15.2",
        "com.twelvemonkeys.common:common-io:3.15.2",
        "com.twelvemonkeys.common:common-image:3.15.2",
    )) {
        "implementation"(library) { isTransitive = false }
        "include"(library) { isTransitive = false }
    }
    if (compat) {
        val runtime = if (obfuscated) "modLocalRuntime" else "localRuntime"
        runtime("maven.modrinth:sodium:${prop("deps.sodium")}")
        runtime("maven.modrinth:iris:${prop("deps.iris")}")
    }
}

// `gradlew :26.2:runClient -Paller.shots=<dir>` runs the self-capture harness (see DevHarness).
providers.gradleProperty("aller.shots").orNull?.let { dir ->
    loom.runs.named("client") { vmArg("-Daller.dev.shots=$dir") }
}
providers.gradleProperty("aller.world").orNull?.let { name ->
    loom.runs.named("client") { vmArg("-Daller.dev.world=$name") }
}
if (providers.gradleProperty("aller.bench").isPresent) {
    loom.runs.named("client") {
        vmArg("-Daller.dev.bench=true")
        if (providers.gradleProperty("aller.bench").get() == "each") vmArg("-Daller.dev.benchEach=true")
    }
}
// The wardrobe looks up past skins for this account instead of the (offline) development one.
providers.gradleProperty("aller.skinUuid").orNull?.let { id ->
    loom.runs.named("client") { vmArg("-Daller.dev.skinUuid=$id") }
}
// Runs the harness's pocket dimension script instead of the usual walk through the screens.
if (providers.gradleProperty("aller.pocket").isPresent) {
    loom.runs.named("client") { vmArg("-Daller.dev.pocket=true") }
}
// Runs the harness's restyled-menu script: every skinned menu, and the hand-over into them part way through.
if (providers.gradleProperty("aller.skin").isPresent) {
    loom.runs.named("client") { vmArg("-Daller.dev.skin=true") }
}
// Runs the harness's pack store script: the button on the pack list, a search, a page, a download.
if (providers.gradleProperty("aller.store").isPresent) {
    loom.runs.named("client") { vmArg("-Daller.dev.store=true") }
}
// Runs the harness's onboarding script: moments of the intro, then each step.
if (providers.gradleProperty("aller.onboarding").isPresent) {
    loom.runs.named("client") { vmArg("-Daller.dev.onboarding=true") }
}
// Runs the harness's updater script: the update popup in each of its states, with a made-up release.
if (providers.gradleProperty("aller.update").isPresent) {
    loom.runs.named("client") { vmArg("-Daller.dev.update=true") }
}
// Runs the harness's screenshots script: the card after a screenshot, the grid, a picture full size, delete and undo.
if (providers.gradleProperty("aller.gallery").isPresent) {
    loom.runs.named("client") { vmArg("-Daller.dev.gallery=true") }
}
// Runs the harness's script for the HUD, item, tab list, chat bubble and photo mode mods in the test world.
if (providers.gradleProperty("aller.mods").isPresent) {
    loom.runs.named("client") { vmArg("-Daller.dev.mods=true") }
}
if (providers.gradleProperty("aller.noWorld").isPresent) {
    loom.runs.named("client") { vmArg("-Daller.dev.noWorld=true") }
}

val javaVersion = prop("mod.java").toInt()

java {
    withSourcesJar()
    val v = JavaVersion.toVersion(javaVersion)
    sourceCompatibility = v
    targetCompatibility = v
}

tasks.withType<JavaCompile>().configureEach {
    options.release = javaVersion
    options.encoding = "UTF-8"
}

// The browser's native library (wry over the system webview) is a Rust crate in native/. It is
// built with cargo and packed into the jar; without a Rust toolchain the build still works and the
// browser reports itself unavailable.
val nativeDir = rootProject.file("native")
val nativeLib = nativeDir.resolve("target/release/aller_webview.dll")
// One task on the root project, shared by every version: they all pack the same file.
val cargoBuild = if ("cargoBuild" in rootProject.tasks.names) rootProject.tasks.named("cargoBuild") else rootProject.tasks.register("cargoBuild") {
    inputs.files(fileTree(nativeDir) { include("src/**", "Cargo.toml", "Cargo.lock") })
    outputs.file(nativeLib).optional()
    onlyIf { System.getProperty("os.name").lowercase().contains("win") }
    val dir = nativeDir
    doLast {
        try {
            val process = ProcessBuilder("cargo", "build", "--release").directory(dir).inheritIO().start()
            if (process.waitFor() != 0) logger.warn("cargo build failed: the jar will have no web browser")
        } catch (e: java.io.IOException) {
            logger.warn("cargo not found: the jar will have no web browser (install Rust from rustup.rs)")
        }
    }
}

tasks.processResources {
    dependsOn(cargoBuild)
    from(nativeDir.resolve("target/release")) {
        include("aller_webview.dll")
        into("natives/windows-x64")
    }
    val props = mapOf(
        "id" to prop("mod.id"),
        "name" to prop("mod.name"),
        "version" to prop("mod.version"),
        "mc" to mc,
        "mc_dep" to prop("mod.mc_dep"),
        "java" to prop("mod.java"),
        "loader" to prop("deps.fabric_loader"),
    )
    inputs.properties(props)
    filesMatching("fabric.mod.json") { expand(props) }

    // Shaders are written against GLSL 150; 26.1+ core shaders (and the Vulkan backend) expect 330.
    // The uniform blocks are inlined in place of #moj_import (as vanilla does for its own startup
    // shaders) so the pipelines can be compiled before any resource pack has loaded.
    val glsl = if (obfuscated) "150" else "330"
    val transforms = "layout(std140) uniform DynamicTransforms { mat4 ModelViewMat; vec4 ColorModulator; vec3 ModelOffset; mat4 TextureMat;" +
        (if (obfuscated) " float LineWidth;" else "") + " };"
    val projection = "layout(std140) uniform Projection { mat4 ProjMat; };"
    inputs.property("glsl", glsl)
    inputs.property("glslTransforms", transforms)
    filesMatching(listOf("**/*.vsh", "**/*.fsh")) {
        filter { line ->
            when {
                line.startsWith("#version") -> "#version $glsl"
                line.contains("minecraft:dynamictransforms.glsl") -> transforms
                line.contains("minecraft:projection.glsl") -> projection
                else -> line
            }
        }
    }
}
