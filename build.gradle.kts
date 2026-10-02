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

tasks.processResources {
    val props = mapOf(
        "id" to prop("mod.id"),
        "name" to prop("mod.name"),
        "version" to prop("mod.version"),
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
