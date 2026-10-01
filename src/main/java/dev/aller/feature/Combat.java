package dev.aller.feature;

import dev.aller.module.Modules;
import dev.aller.platform.Game;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Passive combat statistics derived from what the client already knows: the distance of each
 * attack, whether it connected, and hit streaks. Nothing here changes how attacks behave.
 */
public final class Combat {
    private static int combo;
    private static int comboAge;
    private static double lastReach;
    private static LivingEntity pending;
    private static int pendingAge;
    private static LivingEntity lastVictim;
    private static int victimAge;
    private static int lastHurtTime;

    private Combat() {}

    public static void init() {
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (level.isClientSide() && player == Game.player()) onAttack(entity);
            return InteractionResult.PASS;
        });
    }

    private static void onAttack(Entity target) {
        Vec3 eye = Game.player().getEyePosition();
        AABB box = target.getBoundingBox();
        // Distance to the nearest point of the hitbox, which is what reach limits actually measure.
        double x = Math.clamp(eye.x, box.minX, box.maxX);
        double y = Math.clamp(eye.y, box.minY, box.maxY);
        double z = Math.clamp(eye.z, box.minZ, box.maxZ);
        lastReach = eye.distanceTo(new Vec3(x, y, z));
        if (Modules.HIT_PARTICLES.enabled()) Modules.HIT_PARTICLES.spawn(target);
        if (target instanceof LivingEntity living) {
            pending = living;
            pendingAge = 0;
        }
    }

    public static void tick() {
        var player = Game.player();
        if (player == null) {
            combo = 0;
            pending = null;
            lastVictim = null;
            return;
        }
        // An attack counts as landed once the server makes the target flinch.
        if (pending != null) {
            if (pending.hurtTime > 0 && pending.hurtTime >= pending.hurtDuration - 2) {
                combo++;
                comboAge = 0;
                Session.current().hits++;
                lastVictim = pending;
                victimAge = 0;
                pending = null;
            } else if (++pendingAge > 10) {
                pending = null;
            }
        }
        if (lastVictim != null) {
            if (!lastVictim.isAlive() || lastVictim.isDeadOrDying()) {
                Session.current().kills++;
                lastVictim = null;
            } else if (++victimAge > 40) {
                lastVictim = null;
            }
        }
        // Taking a hit, or three quiet seconds, ends the streak.
        if (player.hurtTime > lastHurtTime) combo = 0;
        lastHurtTime = player.hurtTime;
        if (combo > 0 && ++comboAge > 60) combo = 0;
    }

    public static int combo() {
        return combo;
    }

    public static double lastReach() {
        return lastReach;
    }
}
