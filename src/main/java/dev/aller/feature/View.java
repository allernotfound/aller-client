package dev.aller.feature;

import dev.aller.platform.Mc;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Projects world positions onto the screen, for waypoint markers drawn in the HUD layer. */
public final class View {
    /** Current world field of view in degrees; kept up to date by the FOV hook. */
    public static float fov = 70f;

    private View() {}

    public static Camera camera() {
        //? if <26.1 {
        /*return Mc.mc().gameRenderer.getMainCamera();
        *///?} else {
        return Mc.mc().gameRenderer.mainCamera();
        //?}
    }

    /**
     * @return {x, y, depth} in GUI pixels, where depth &gt; 0 means in front of the camera; x and y
     *         are still meaningful (mirrored) when behind, so callers can clamp markers to an edge
     */
    public static float[] project(double wx, double wy, double wz, float screenW, float screenH) {
        Camera cam = camera();
        Vec3 pos = cam.position();
        Vector3f rel = new Vector3f((float) (wx - pos.x), (float) (wy - pos.y), (float) (wz - pos.z));
        // Into camera space: the camera's rotation maps view space to world space, so invert it.
        new Quaternionf(cam.rotation()).conjugate().transform(rel);
        float depth = -rel.z;
        float f = (float) (1.0 / Math.tan(Math.toRadians(fov) / 2));
        float aspect = screenW / screenH;
        float d = Math.max(Math.abs(depth), 0.001f);
        float ndcX = rel.x * f / aspect / d;
        float ndcY = rel.y * f / d;
        return new float[] {(ndcX * 0.5f + 0.5f) * screenW, (0.5f - ndcY * 0.5f) * screenH, depth};
    }
}
