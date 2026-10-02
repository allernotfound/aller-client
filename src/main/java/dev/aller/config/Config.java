package dev.aller.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aller.AllerClient;
import dev.aller.module.Module;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Persistence. Client options live in {@code client.json}; everything about modules and the HUD
 * layout lives in a named profile under {@code profiles/}, so whole setups can be swapped at once.
 */
public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final String DEFAULT_PROFILE = "default";

    private final Path dir = FabricLoader.getInstance().getConfigDir().resolve(AllerClient.ID);
    private final Path profiles = dir.resolve("profiles");
    private String active = DEFAULT_PROFILE;
    private boolean dirty;
    private int ticksSinceDirty;
    /** Extra sections owned by features (waypoints, profile rules...), stored in client.json. */
    private JsonObject extras = new JsonObject();

    public Path dir() {
        return dir;
    }

    public String activeProfile() {
        return active;
    }

    public void load() {
        JsonObject root = read(dir.resolve("client.json"));
        if (root != null) {
            try {
                if (root.has("options") && root.get("options").isJsonObject()) AllerClient.options().load(root.getAsJsonObject("options"));
                if (root.has("profile") && root.get("profile").isJsonPrimitive()) active = sanitise(root.get("profile").getAsString());
                if (root.has("extras") && root.get("extras").isJsonObject()) extras = root.getAsJsonObject("extras");
            } catch (RuntimeException e) {
                AllerClient.LOG.warn("Ignoring unreadable parts of client.json", e);
            }
        }
        AllerClient.options().applyMotion();
        loadProfile(active);
    }

    public JsonElement extra(String key) {
        return extras.get(key);
    }

    public void setExtra(String key, JsonElement value) {
        extras.add(key, value);
        markDirty();
    }

    public void markDirty() {
        dirty = true;
        ticksSinceDirty = 0;
    }

    /** Debounced autosave: writes two seconds after the last change. */
    public void tick() {
        if (dirty && ++ticksSinceDirty > 40) save();
    }

    public void save() {
        dirty = false;
        JsonObject root = new JsonObject();
        root.add("options", AllerClient.options().save());
        root.addProperty("profile", active);
        root.add("extras", extras);
        write(dir.resolve("client.json"), root);
        saveProfile(active);
    }

    private void saveProfile(String name) {
        JsonObject modules = new JsonObject();
        for (Module m : AllerClient.modules().all()) modules.add(m.id, m.save());
        JsonObject root = new JsonObject();
        root.add("modules", modules);
        write(profiles.resolve(name + ".json"), root);
    }

    private void loadProfile(String name) {
        JsonObject root = read(profiles.resolve(name + ".json"));
        if (root == null || !root.has("modules") || !root.get("modules").isJsonObject()) return;
        JsonObject modules = root.getAsJsonObject("modules");
        for (Module m : AllerClient.modules().all()) {
            // Start from defaults so anything the file omits doesn't leak in from the previous profile.
            m.resetToDefaults();
            if (modules.has(m.id) && modules.get(m.id).isJsonObject()) {
                try {
                    m.load(modules.getAsJsonObject(m.id));
                } catch (RuntimeException e) {
                    AllerClient.LOG.warn("Skipping bad config for module {}", m.id, e);
                }
            }
        }
    }

    public List<String> profileNames() {
        List<String> names = new ArrayList<>();
        names.add(DEFAULT_PROFILE);
        if (Files.isDirectory(profiles)) {
            try (Stream<Path> files = Files.list(profiles)) {
                files.map(p -> p.getFileName().toString())
                        .filter(n -> n.endsWith(".json"))
                        .map(n -> n.substring(0, n.length() - 5))
                        .filter(n -> !n.equals(DEFAULT_PROFILE))
                        .sorted()
                        .forEach(names::add);
            } catch (IOException e) {
                AllerClient.LOG.warn("Could not list profiles", e);
            }
        }
        return names;
    }

    /** Saves the current profile, then loads another. A profile that doesn't exist yet starts as a copy. */
    public void switchProfile(String name) {
        name = sanitise(name);
        if (name.equals(active)) return;
        save();
        boolean exists = Files.exists(profiles.resolve(name + ".json"));
        active = name;
        if (exists) loadProfile(name);
        save();
    }

    public void deleteProfile(String name) {
        if (name.equals(DEFAULT_PROFILE)) return;
        if (name.equals(active)) switchProfile(DEFAULT_PROFILE);
        try {
            Files.deleteIfExists(profiles.resolve(name + ".json"));
        } catch (IOException e) {
            AllerClient.LOG.warn("Could not delete profile {}", name, e);
        }
    }

    public static String sanitise(String name) {
        String s = name.trim().toLowerCase().replaceAll("[^a-z0-9 _-]", "").replace(' ', '-');
        return s.isEmpty() ? DEFAULT_PROFILE : s.length() > 24 ? s.substring(0, 24) : s;
    }

    private static JsonObject read(Path file) {
        if (!Files.exists(file)) return null;
        try {
            JsonElement e = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            return e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (Exception e) {
            AllerClient.LOG.warn("Could not read {}, using defaults", file, e);
            return null;
        }
    }

    private static void write(Path file, JsonObject json) {
        try {
            Files.createDirectories(file.getParent());
            // Write-then-rename so a crash mid-save can't leave a truncated config.
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(json), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            AllerClient.LOG.error("Could not save {}", file, e);
        }
    }
}
