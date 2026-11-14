package org.sawiq.voicetranslate.client.hud;

import java.util.Optional;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

/**
 * Finds where a point in the world appears on the HUD.
 *
 * <p>Done from the camera's own axes and field of view rather than the
 * renderer's matrices, which Minecraft rebuilds and renames from release to
 * release; this needs only a position, three vectors and an angle.</p>
 */
final class ScreenProjector {
    /** A point on screen, in GUI pixels, and how far in front of the camera it is. */
    record Projection(float x, float y, double distance) {
    }

    private static final double NEAR_PLANE = 0.05;

    private ScreenProjector() {
    }

    static Optional<Projection> project(Camera camera, float fovDegrees, Vec3 point, int guiWidth, int guiHeight) {
        Vec3 offset = point.subtract(camera.position());
        Vector3fc forward = camera.forwardVector();
        Vector3fc up = camera.upVector();
        Vector3fc left = camera.leftVector();

        double depth = offset.x * forward.x() + offset.y * forward.y() + offset.z * forward.z();
        if (depth < NEAR_PLANE) {
            return Optional.empty();
        }
        double right = -(offset.x * left.x() + offset.y * left.y() + offset.z * left.z());
        double above = offset.x * up.x() + offset.y * up.y() + offset.z * up.z();

        double focal = (guiHeight / 2.0) / Math.tan(Math.toRadians(fovDegrees) / 2.0);
        float x = (float) (guiWidth / 2.0 + right / depth * focal);
        float y = (float) (guiHeight / 2.0 - above / depth * focal);
        boolean isOnScreen = x >= 0 && x <= guiWidth && y >= 0 && y <= guiHeight;
        return isOnScreen ? Optional.of(new Projection(x, y, offset.length())) : Optional.empty();
    }
}
