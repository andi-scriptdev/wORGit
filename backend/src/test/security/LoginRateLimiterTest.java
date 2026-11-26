package edu.bsu.worgit.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.bsu.worgit.exception.TooManyAttemptsException;
import edu.bsu.worgit.support.TestClock;
import edu.bsu.worgit.support.TestData;

class LoginRateLimiterTest {

    private TestClock clock;
    private LoginRateLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new TestClock();
        limiter = new LoginRateLimiter(TestData.props(), clock);   // 5 failures per 15 minutes
    }

    private void fail(int times, String id, String ip) {
        for (int i = 0; i < times; i++) {
            limiter.recordFailure(id, ip);
        }
    }

    @Test
    void blocksAfterFiveFailures() {
        fail(4, "23-10457", "1.1.1.1");
        assertThatCode(() -> limiter.checkAllowed("23-10457", "1.1.1.1")).doesNotThrowAnyException();
        fail(1, "23-10457", "1.1.1.1");
        assertThatThrownBy(() -> limiter.checkAllowed("23-10457", "1.1.1.1"))
                .isInstanceOf(TooManyAttemptsException.class);
    }

    @Test
    void otherIpAndOtherIdAreNotAffected() {
        fail(5, "23-10457", "1.1.1.1");
        assertThatCode(() -> limiter.checkAllowed("23-10457", "2.2.2.2")).doesNotThrowAnyException();
        assertThatCode(() -> limiter.checkAllowed("23-10458", "1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void idsAreComparedIgnoringCase() {
        fail(5, "ADM-001", "1.1.1.1");
        assertThatThrownBy(() -> limiter.checkAllowed("adm-001", "1.1.1.1"))
                .isInstanceOf(TooManyAttemptsException.class);
    }

    @Test
    void blockLiftsAfterTheWindow() {
        fail(5, "23-10457", "1.1.1.1");
        clock.advance(Duration.ofMinutes(16));
        assertThatCode(() -> limiter.checkAllowed("23-10457", "1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void oldFailuresDoNotCount() {
        fail(4, "23-10457", "1.1.1.1");
        clock.advance(Duration.ofMinutes(16));
        fail(1, "23-10457", "1.1.1.1");
        assertThatCode(() -> limiter.checkAllowed("23-10457", "1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void resetClearsTheCounter() {
        fail(5, "23-10457", "1.1.1.1");
        limiter.reset("23-10457", "1.1.1.1");
        assertThatCode(() -> limiter.checkAllowed("23-10457", "1.1.1.1")).doesNotThrowAnyException();
    }
}
