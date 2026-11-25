package edu.bsu.worgit.domain;

import java.util.List;
import java.util.Optional;

/** Milestones First Event / 5 / 10 events attended (thresholds 1, 5, 10 are ASSUMED in the spec). */
public final class Achievements {
    private Achievements() {}

    public record Milestone(String title, int threshold) {}

    public static final List<Milestone> MILESTONES = List.of(
            new Milestone("First Event", 1),
            new Milestone("5 Events Attended", 5),
            new Milestone("10 Events Attended", 10));

    public static List<Milestone> earned(int attended) {
        return MILESTONES.stream().filter(m -> attended >= m.threshold()).toList();
    }

    public static Optional<Milestone> next(int attended) {
        return MILESTONES.stream().filter(m -> attended < m.threshold()).findFirst();
    }

    /** e.g. "5 of 10 done" for the dashboard's next-badge row. */
    public static String progress(int attended, Milestone milestone) {
        return attended + " of " + milestone.threshold() + " done";
    }
}
