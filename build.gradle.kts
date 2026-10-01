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

repositories {
    maven("https://maven.terraformersmc.com/")
}

val loom = extensions.getByType<LoomGradleExtensionAPI>()
val modImpl = if (obfuscated) "modImplementation" else "implementation"

dependencies {
    "minecraft"("com.mojang:minecraft:$mc")
    if (obfuscated) "mappings"(loom.officialMojangMappings())
    modImpl("net.fabricmc:fabric-loader:${prop("deps.fabric_loader")}")
    modImpl("net.fabricmc.fabric-api:fabric-api:${prop("deps.fabric_api")}")
    modImpl("com.terraformersmc:modmenu:${prop("deps.modmenu")}")
}

// `gradlew :26.2:runClient -Paller.shots=<dir>` runs the self-capture harness (see DevHarness).
providers.gradleProperty("aller.shots").orNull?.let { dir ->
    loom.runs.named("client") { vmArg("-Daller.dev.shots=$dir") }
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
    val glsl = if (obfuscated) "150" else "330"
    inputs.property("glsl", glsl)
    filesMatching(listOf("**/*.vsh", "**/*.fsh")) {
        filter { line -> if (line.startsWith("#version")) "#version $glsl" else line }
    }
}
