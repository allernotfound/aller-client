package dev.aller.feature;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * The room the pocket opens into: a hollow box of black concrete in a void world, with a lit
 * doorway in the far wall. Everything inside is the player's to build on; the box itself is put
 * back whenever something (a hand, an explosion, a piston) changes it.
 */
public final class PocketRoom {
    private PocketRoom() {}

    /** Inside measurements, in blocks. */
    public static final int WIDTH = 32, HEIGHT = 16, DEPTH = 32;
    public static final int FLOOR = 64;
    /** Where the player arrives, looking down the room at the door. */
    public static final double ENTRY_X = WIDTH / 2.0, ENTRY_Y = FLOOR, ENTRY_Z = 4.5;

    private static final int DOOR_X = WIDTH / 2 - 1, DOOR_W = 2, DOOR_H = 3, DOOR_DEPTH = 2;

    private record Piece(BlockPos pos, BlockState state) {}

    private static List<Piece> plan;

    /** The block the room needs at a position, or null where the player may build. */
    private static BlockState wanted(int x, int y, int z) {
        boolean doorColumn = x >= DOOR_X && x < DOOR_X + DOOR_W && y >= FLOOR && y < FLOOR + DOOR_H;
        boolean frame = x >= DOOR_X - 1 && x <= DOOR_X + DOOR_W && y >= FLOOR - 1 && y <= FLOOR + DOOR_H;
        // The passage behind the doorway: air, wrapped in light.
        if (z >= DEPTH && z <= DEPTH + DOOR_DEPTH + 1 && frame) {
            if (doorColumn && z <= DEPTH + DOOR_DEPTH) return Blocks.AIR.defaultBlockState();
            if (z > DEPTH || y >= FLOOR) return Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState();
        }
        boolean inX = x >= -1 && x <= WIDTH, inY = y >= FLOOR - 1 && y <= FLOOR + HEIGHT, inZ = z >= -1 && z <= DEPTH;
        if (!inX || !inY || !inZ) return null;
        boolean shell = x == -1 || x == WIDTH || y == FLOOR - 1 || y == FLOOR + HEIGHT || z == -1 || z == DEPTH;
        return shell ? dev.aller.platform.Game.blackConcrete() : null;
    }

    private static List<Piece> plan() {
        if (plan == null) {
            List<Piece> out = new ArrayList<>();
            for (int x = -1; x <= WIDTH; x++) {
                for (int y = FLOOR - 1; y <= FLOOR + HEIGHT; y++) {
                    for (int z = -1; z <= DEPTH + DOOR_DEPTH + 1; z++) {
                        BlockState state = wanted(x, y, z);
                        if (state != null) out.add(new Piece(new BlockPos(x, y, z), state));
                    }
                }
            }
            plan = out;
        }
        return plan;
    }

    /** Builds the room, or repairs whatever of it has changed. Runs on the server thread. */
    public static void repair(ServerLevel level) {
        for (Piece piece : plan()) {
            if (level.getBlockState(piece.pos) != piece.state) level.setBlock(piece.pos, piece.state, 3);
        }
    }

    /** Whether a block belongs to the room itself and so cannot be broken or built over. */
    public static boolean fixed(BlockPos pos) {
        return wanted(pos.getX(), pos.getY(), pos.getZ()) != null;
    }

    /** Whether something has walked through the doorway. */
    public static boolean throughDoor(Entity entity) {
        return entity.getZ() >= DEPTH + 0.7 && entity.getZ() < DEPTH + DOOR_DEPTH + 1
                && entity.getX() >= DOOR_X && entity.getX() < DOOR_X + DOOR_W
                && entity.getY() >= FLOOR - 0.5 && entity.getY() < FLOOR + DOOR_H;
    }
}
