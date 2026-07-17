package com.ninuna.losttales.client.camera;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure tick-by-tick projectile prediction with optional segment collision. */
public final class ProjectileTrajectorySolver {
    public interface CollisionResolver {
        TargetingVector resolve(
                TargetingVector start, TargetingVector end);
    }

    private ProjectileTrajectorySolver() {}

    public static List<TargetingVector> predict(
            TargetingVector origin, TargetingVector direction,
            ProjectileBallisticsProfile profile,
            double maximumDistance, int maximumTicks,
            CollisionResolver collisionResolver) {
        if (origin == null || direction == null || profile == null) {
            throw new IllegalArgumentException(
                    "origin, direction, and profile are required");
        }
        CameraMath.requireNonNegativeFinite(
                "maximumDistance", maximumDistance);
        if (maximumDistance <= 0.0D
                || maximumTicks <= 0 || maximumTicks > 400) {
            throw new IllegalArgumentException(
                    "distance must be positive and ticks must be in [1, 400]");
        }
        double directionLengthSquared = direction.lengthSquared();
        if (directionLengthSquared <= 0.000000000001D) {
            throw new IllegalArgumentException(
                    "direction must not be zero");
        }

        TargetingVector velocity = direction.scale(
                profile.getLaunchSpeed()
                        / Math.sqrt(directionLengthSquared));
        TargetingVector position = origin;
        double travelled = 0.0D;
        List<TargetingVector> points =
                new ArrayList<TargetingVector>(maximumTicks + 1);
        points.add(origin);

        for (int tick = 0; tick < maximumTicks; tick++) {
            double stepLength = Math.sqrt(velocity.lengthSquared());
            if (stepLength <= 0.0000001D) {
                break;
            }
            double remaining = maximumDistance - travelled;
            boolean distanceLimited = stepLength >= remaining;
            TargetingVector end = position.add(velocity.scale(
                    distanceLimited ? remaining / stepLength : 1.0D));
            TargetingVector collision = collisionResolver == null
                    ? null : collisionResolver.resolve(position, end);
            if (collision != null) {
                points.add(collision);
                break;
            }
            points.add(end);
            if (distanceLimited) {
                break;
            }

            travelled += stepLength;
            position = end;
            velocity = new TargetingVector(
                    velocity.getX() * profile.getDrag(),
                    velocity.getY() * profile.getDrag()
                            - profile.getGravity(),
                    velocity.getZ() * profile.getDrag());
        }
        return Collections.unmodifiableList(points);
    }
}
