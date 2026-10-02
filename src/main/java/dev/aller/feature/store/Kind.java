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
    RESOURCE_PACKS("resourcepack", "Resource packs", "pack", List.of("minecraft"), List.of(".zip"),
            () -> Mc.mc().getResourcePackDirectory());

    /** Modrinth's {@code project_type}. */
    public final String type;
    public final String title;
    /** What one of them is called in a sentence. */
    public final String noun;
    /** Modrinth's loaders a file must be for; resource packs have the one, "minecraft". */
    public final List<String> loaders;
    /** File endings that count as one of these in the folder. */
    public final List<String> endings;
    private final Supplier<Path> dir;

    Kind(String type, String title, String noun, List<String> loaders, List<String> endings, Supplier<Path> dir) {
        this.type = type;
        this.title = title;
        this.noun = noun;
        this.loaders = loaders;
        this.endings = endings;
        this.dir = dir;
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
