package com.jmeternext.engine.service.shape;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * A load shape composed of multiple sequential stages, each ramping linearly
 * from the previous stage's target to this stage's target over the stage's duration.
 *
 * <p>The first stage ramps from 0 users. Each subsequent stage begins where the
 * previous one ended. Returns {@code null} after all stages complete.
 *
 * <p>Example: ramp up, hold, ramp down:
 * <pre>{@code
 * new StagesShape(List.of(
 *     new Stage(Duration.ofMinutes(2), 100),  // ramp 0 → 100
 *     new Stage(Duration.ofMinutes(5), 100),  // hold at 100
 *     new Stage(Duration.ofMinutes(1), 0)     // ramp 100 → 0
 * ))
 * }</pre>
 */
public final class StagesShape implements LoadShape {

    /**
     * A single stage in a multi-stage load profile.
     *
     * @param duration    how long this stage lasts
     * @param targetUsers the user count at the end of this stage
     */
    public record Stage(Duration duration, int targetUsers) {
        public Stage {
            Objects.requireNonNull(duration, "duration must not be null");
            if (duration.isNegative() || duration.isZero()) {
                throw new IllegalArgumentException("duration must be positive");
            }
            if (targetUsers < 0) {
                throw new IllegalArgumentException("targetUsers must be >= 0");
            }
        }
    }

    private final List<Stage> stages;
    private final long[] cumulativeMillis;
    private final int[] stageStartUsers;

    /**
     * Constructs a stages shape.
     *
     * @param stages the ordered list of stages; must not be {@code null} or empty
     * @throws IllegalArgumentException if {@code stages} is empty
     * @throws NullPointerException     if {@code stages} is {@code null}
     */
    public StagesShape(List<Stage> stages) {
        Objects.requireNonNull(stages, "stages must not be null");
        if (stages.isEmpty()) throw new IllegalArgumentException("stages must not be empty");
        this.stages = List.copyOf(stages);

        this.cumulativeMillis = new long[this.stages.size()];
        this.stageStartUsers = new int[this.stages.size()];

        long cumulative = 0;
        int previousTarget = 0;
        for (int i = 0; i < this.stages.size(); i++) {
            stageStartUsers[i] = previousTarget;
            cumulative += this.stages.get(i).duration().toMillis();
            cumulativeMillis[i] = cumulative;
            previousTarget = this.stages.get(i).targetUsers();
        }
    }

    /**
     * Returns a tick with the interpolated user count for the current stage,
     * or {@code null} after all stages complete.
     *
     * @param elapsed      time since test start
     * @param currentUsers currently active virtual user count (unused)
     * @return shape tick, or {@code null} when all stages have completed
     */
    @Override
    public ShapeTick tick(Duration elapsed, int currentUsers) {
        long elapsedMillis = elapsed.toMillis();

        long stageStart = 0;
        for (int i = 0; i < stages.size(); i++) {
            if (elapsedMillis <= cumulativeMillis[i]) {
                Stage stage = stages.get(i);
                int from = stageStartUsers[i];
                int to = stage.targetUsers();
                long stageDurationMillis = stage.duration().toMillis();
                long elapsedInStage = elapsedMillis - stageStart;

                double progress = (double) elapsedInStage / stageDurationMillis;
                progress = Math.min(progress, 1.0);
                int users = (int) Math.round(from + (to - from) * progress);
                double spawnRate = Math.abs(to - from) / (double) stage.duration().toSeconds();

                return new ShapeTick(users, spawnRate);
            }
            stageStart = cumulativeMillis[i];
        }
        return null;
    }
}
