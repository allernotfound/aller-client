package dev.aller.module;

import dev.aller.AllerClient;
import dev.aller.platform.Mc;
import dev.aller.platform.Sounds;
import dev.aller.ui.Toasts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ModuleManager {
    private final Map<String, Module> byId = new LinkedHashMap<>();
    private final List<Module> all = new ArrayList<>();
    private final Map<Module, Boolean> keyState = new HashMap<>();
    /** Restricted modules the user has already been warned about this session. */
    private final Set<String> warned = new HashSet<>();

    public <M extends Module> M register(M module) {
        if (byId.putIfAbsent(module.id, module) != null) {
            throw new IllegalStateException("Duplicate module id " + module.id);
        }
        all.add(module);
        return module;
    }

    public List<Module> all() {
        return all;
    }

    public Module get(String id) {
        return byId.get(id);
    }

    @SuppressWarnings("unchecked")
    public <M extends Module> M get(Class<M> type) {
        for (Module m : all) if (type.isInstance(m)) return (M) m;
        throw new IllegalArgumentException("Module not registered: " + type.getSimpleName());
    }

    public List<Module> in(Category category) {
        List<Module> out = new ArrayList<>();
        for (Module m : all) if (m.category == category) out.add(m);
        return out;
    }

    /** Toggle from the UI or a keybind: plays feedback, shows a toast and saves. */
    public void userToggle(Module m) {
        m.toggle();
        Sounds.toggle(m.enabled());
        if (AllerClient.options().toasts.get()) {
            Toasts.show(m.name, m.enabled() ? "Enabled" : "Disabled", m.enabled());
        }
        if (m.enabled() && m.fairPlayNote != null && AllerClient.options().fairPlayWarnings.get() && warned.add(m.id)) {
            Toasts.warn("Check server rules", m.fairPlayNote);
        }
        if (m.enabled() && m.experimentalNote != null && warned.add(m.id + ".experimental")) {
            Toasts.warn(m.name + " is experimental", m.experimentalNote);
        }
        AllerClient.config().markDirty();
    }

    public void tick() {
        if (Mc.mc().player == null) return;
        for (Module m : all) if (m.enabled()) m.tick();
    }

    /** Reads module keys once a frame, so a quick tap between two game ticks still counts. */
    public void pollKeys() {
        // Ctrl with a letter belongs to the screenshot card while it is up.
        boolean canUseKeys = Mc.mc().player != null && Mc.screen() == null && !dev.aller.ui.ShotCard.claiming();
        for (Module m : all) {
            if (m.ownKey) continue;
            boolean down = canUseKeys && Mc.isDown(m.keybind.get());
            boolean was = Boolean.TRUE.equals(keyState.put(m, down));
            if (m.holdToActivate()) m.keyInput(down, down && !was, canUseKeys);
            else if (down && !was) userToggle(m);
        }
    }
}
