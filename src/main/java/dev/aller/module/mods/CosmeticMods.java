package dev.aller.module.mods;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.aller.module.Category;
import dev.aller.module.Module;
import dev.aller.platform.Game;
import dev.aller.platform.Mc;
import dev.aller.setting.Settings;
import dev.aller.ui.Theme;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;

/** Style extras. Everything here is client-side: other players see nothing different. */
public final class CosmeticMods {
    private CosmeticMods() {}

    public static final class Cape extends Module {
        public enum Style { GRADIENT, MIDNIGHT, AURORA }

        public final Settings.Choice<Style> style = choice("style", "Design", Style.GRADIENT);
        public final Settings.Bool followAccent = bool("follow_accent", "Use the accent colour", true);
        public final Settings.Color color = add(new Settings.Color("color", "Colour", 0xFF8B5CF6, false));

        public Cape() {
            super("cape", "Aller Client cape", "Wear a cape in your accent colour. Only you can see it", Category.COSMETIC);
            keywords("cloak", "elytra", "skin");
            color.visibleWhen(() -> !followAccent.get());
        }

        public int tint() {
            return followAccent.get() ? Theme.accent() : color.get() | 0xFF000000;
        }
    }

    public static final class OwnNametag extends Module {
        public OwnNametag() {
            super("own_nametag", "Own nametag", "Show your own name above your head in third person", Category.COSMETIC);
            keywords("name", "f5", "third person");
        }
    }

    public static final class HitParticles extends Module {
        public enum Kind { CRIT, MAGIC, HEARTS, FLAME, SPARKLE, CLOUD }

        public final Settings.Choice<Kind> kind = choice("kind", "Particles", Kind.MAGIC);
        public final Settings.Num amount = num("amount", "Amount", 2f, 1f, 5f, 1f).suffix("x");

        public HitParticles() {
            super("hit_particles", "Hit particles", "A burst of particles from whatever you hit. Only you can see them", Category.COSMETIC);
            keywords("crit", "sharpness", "attack", "effects");
        }

        public void spawn(Entity target) {
            SimpleParticleType type = switch (kind.get()) {
                case CRIT -> ParticleTypes.CRIT;
                case MAGIC -> ParticleTypes.ENCHANTED_HIT;
                case HEARTS -> ParticleTypes.HEART;
                case FLAME -> ParticleTypes.FLAME;
                case SPARKLE -> ParticleTypes.END_ROD;
                case CLOUD -> ParticleTypes.CLOUD;
            };
            for (int i = 0; i < amount.asInt(); i++) Mc.mc().particleEngine.createTrackingEmitter(target, type);
        }
    }

    public static final class Viewmodel extends Module {
        public final Settings.Num scale = num("scale", "Size", 0.8f, 0.4f, 1.5f, 0.05f).suffix("x");
        public final Settings.Num x = num("x", "Sideways", 0f, -0.6f, 0.6f, 0.02f);
        public final Settings.Num y = num("y", "Height", 0f, -0.6f, 0.6f, 0.02f);
        public final Settings.Num z = num("z", "Distance", 0f, -0.8f, 0.8f, 0.02f);

        public Viewmodel() {
            super("viewmodel", "Held item view", "Resize and reposition the item in your hand", Category.COSMETIC);
            keywords("viewmodel", "hand", "small items", "item size", "position");
        }

        public void apply(PoseStack pose, boolean mainHand) {
            var player = Game.player();
            boolean right = player == null || mainHand == (player.getMainArm() == HumanoidArm.RIGHT);
            float side = right ? 1 : -1, s = scale.get();
            pose.translate(x.get() * side, y.get(), -z.get());
            // Scale around where the hand rests. Scaling around the camera itself would change
            // nothing on screen, since perspective cancels it out.
            pose.translate(0.56f * side * (1 - s), -0.52f * (1 - s), -0.72f * (1 - s));
            pose.scale(s, s, s);
        }
    }
}
