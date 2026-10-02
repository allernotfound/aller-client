package dev.aller.feature.store;

import dev.aller.platform.Mc;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

/**
 * A sort of thing the store can fetch from Modrinth. Everything else in the store takes one of
 * these, so shader packs or mods are another entry here and a place to open the screen from.
 */
public enum Kind {
    RESOURCE_PACKS("resourcepack", "Resource packs", "pack", "Get more packs", "It is at the top of your available packs.",
            List.of("minecraft"), false, List.of(".zip"), () -> Mc.mc().getResourcePackDirectory()),
    /** Shaders in the format Iris loads, which is also OptiFine's: the two loaders Modrinth files them under. */
    SHADER_PACKS("shader", "Shader packs", "shader pack", "Get more shaders", "It is picked out in your shader packs.",
            List.of("iris", "optifine"), true, List.of(".zip"), Kind::shaderDir);

    /** Modrinth's {@code project_type}. */
    public final String type;
    public final String title;
    /** What one of them is called in a sentence. */
    public final String noun;
    /** What the button on the game's own list says. */
    public final String invite;
    /** Where a download can be found afterwards, for the toast. */
    public final String landed;
    /** Modrinth's loaders a file must be for; resource packs have the one, "minecraft". */
    public final List<String> loaders;
    /** Whether a search has to name the loaders too: only where a project type has more than this game can use. */
    public final boolean searchLoaders;
    /** File endings that count as one of these in the folder. */
    public final List<String> endings;
    private final Supplier<Path> dir;

    Kind(String type, String title, String noun, String invite, String landed, List<String> loaders, boolean searchLoaders,
            List<String> endings, Supplier<Path> dir) {
        this.type = type;
        this.title = title;
        this.noun = noun;
        this.invite = invite;
        this.landed = landed;
        this.loaders = loaders;
        this.searchLoaders = searchLoaders;
        this.endings = endings;
        this.dir = dir;
    }

    /** Iris's folder, asked of Iris in case it has been moved; the usual place otherwise. */
    private static Path shaderDir() {
        try {
            Object dir = Class.forName("net.irisshaders.iris.Iris").getMethod("getShaderpacksDirectory").invoke(null);
            if (dir instanceof Path path) return path;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // no Iris, or another version of it
        }
        return Mc.mc().gameDirectory.toPath().resolve("shaderpacks");
    }

    /** Where downloads go. */
    public Path dir() {
        return dir.get();
    }

    public boolean holds(String filename) {
        String lower = filename.toLowerCase(java.util.Locale.ROOT);
        for (String ending : endings) if (lower.endsWith(ending)) return true;
        return false;
    }
}
