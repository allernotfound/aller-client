package dev.aller.mixin.pocket;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Watches the pocket dimension's mixins being applied. They live in their own config, which is not
 * required and whose injectors may find nothing, so another mod that changes the same code can
 * never stop the game from starting; this records what actually went in, and the pocket refuses to
 * open unless all of it did.
 */
public final class PocketPlugin implements IMixinConfigPlugin {
    /** Injector handlers in these mixins are named with this, which is how a missed one is found. */
    private static final String HANDLER = "pocket$";
    /** Mixin class to the class it changes. */
    private static final Map<String, String> EXPECTED = new ConcurrentHashMap<>();
    private static final Set<String> APPLIED = ConcurrentHashMap.newKeySet();
    private static final Set<String> MISSED = ConcurrentHashMap.newKeySet();

    /** What is wrong, in a few words, or null when every mixin and injector is in place. */
    public static String problem() {
        for (Map.Entry<String, String> e : EXPECTED.entrySet()) {
            if (APPLIED.contains(e.getKey())) continue;
            // Mixins go in when their class loads, so make sure it has before calling it missing.
            try {
                Class.forName(e.getValue(), true, PocketPlugin.class.getClassLoader());
            } catch (Throwable t) {
                return simple(e.getValue()) + " could not be loaded";
            }
            if (!APPLIED.contains(e.getKey())) return simple(e.getValue()) + " was changed by another mod";
        }
        if (EXPECTED.isEmpty()) return "its mixins were not loaded";
        return MISSED.isEmpty() ? null : MISSED.iterator().next() + " could not be hooked";
    }

    private static String simple(String name) {
        return name.substring(name.lastIndexOf('.') + 1);
    }

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        EXPECTED.put(mixinClassName, targetClassName);
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        APPLIED.add(mixinClassName);
        // A handler is merged in even when its injector found nowhere to go; it is then never called.
        Set<String> handlers = new HashSet<>(), called = new HashSet<>();
        for (MethodNode method : targetClass.methods) {
            if (method.name.contains(HANDLER)) handlers.add(method.name);
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof MethodInsnNode call && call.name.contains(HANDLER)) called.add(call.name);
            }
        }
        handlers.removeAll(called);
        for (String name : handlers) MISSED.add(simple(targetClassName) + "." + name.substring(name.indexOf(HANDLER) + HANDLER.length()));
    }
}
