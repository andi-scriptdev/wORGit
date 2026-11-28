package edu.bsu.worgit.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** A clock tests can move forward. */
public class TestClock extends Clock {
    private Instant now;
    private final ZoneId zone;

    public TestClock() {
        this(Instant.parse("2026-11-24T02:00:00Z"), ZoneId.of("Asia/Manila"));
    }

    private TestClock(Instant now, ZoneId zone) {
        this.now = now;
        this.zone = zone;
    }

    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new TestClock(now, zone);
    }

    @Override
    public Instant instant() {
        return now;
    }
}
