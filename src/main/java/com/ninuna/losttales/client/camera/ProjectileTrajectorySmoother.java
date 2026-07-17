package com.ninuna.losttales.client.camera;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure cubic-Hermite resampling for the client-side trajectory guide. */
public final class ProjectileTrajectorySmoother {
    private ProjectileTrajectorySmoother() {}

    public static List<TargetingVector> resample(
            List<TargetingVector> points, int samplesPerSegment,
            double tangentScale) {
        if (points == null) {
            throw new IllegalArgumentException("points are required");
        }
        if (samplesPerSegment < 1 || samplesPerSegment > 16) {
            throw new IllegalArgumentException(
                    "samplesPerSegment must be in [1, 16]");
        }
        CameraMath.requireNonNegativeFinite("tangentScale", tangentScale);
        if (tangentScale > 0.5D) {
            throw new IllegalArgumentException(
                    "tangentScale must be at most 0.5");
        }
        if (points.size() < 2) {
            return Collections.unmodifiableList(
                    new ArrayList<TargetingVector>(points));
        }

        int segmentCount = points.size() - 1;
        List<TargetingVector> result = new ArrayList<TargetingVector>(
                segmentCount * samplesPerSegment + 1);
        result.add(require(points.get(0)));
        for (int segment = 0; segment < segmentCount; segment++) {
            TargetingVector previous = require(points.get(
                    Math.max(0, segment - 1)));
            TargetingVector start = require(points.get(segment));
            TargetingVector end = require(points.get(segment + 1));
            TargetingVector next = require(points.get(
                    Math.min(points.size() - 1, segment + 2)));
            TargetingVector startTangent = end.subtract(previous)
                    .scale(tangentScale);
            TargetingVector endTangent = next.subtract(start)
                    .scale(tangentScale);

            for (int sample = 1; sample <= samplesPerSegment; sample++) {
                double t = (double)sample / samplesPerSegment;
                result.add(interpolate(
                        start, end, startTangent, endTangent, t));
            }
        }
        return Collections.unmodifiableList(result);
    }

    private static TargetingVector interpolate(
            TargetingVector start, TargetingVector end,
            TargetingVector startTangent,
            TargetingVector endTangent, double t) {
        double tSquared = t * t;
        double tCubed = tSquared * t;
        double startWeight = 2.0D * tCubed
                - 3.0D * tSquared + 1.0D;
        double startTangentWeight = tCubed
                - 2.0D * tSquared + t;
        double endWeight = -2.0D * tCubed + 3.0D * tSquared;
        double endTangentWeight = tCubed - tSquared;
        return start.scale(startWeight)
                .add(startTangent.scale(startTangentWeight))
                .add(end.scale(endWeight))
                .add(endTangent.scale(endTangentWeight));
    }

    private static TargetingVector require(TargetingVector value) {
        if (value == null) {
            throw new IllegalArgumentException(
                    "trajectory points must not be null");
        }
        return value;
    }
}
