package br.com.breno.penssay.web;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class UsageLimiterTest {

    // 10:00 in Brasília (UTC-3), far from midnight so the daily cap does not reset during a test
    private static final Instant START = Instant.parse("2026-11-08T13:00:00Z");
    private static final String ALICE = "203.0.113.1";
    private static final String BOB = "203.0.113.2";

    private final MutableClock clock = new MutableClock(START);

    private UsageLimiter limiter(int perOrigin, int perDay) {
        return limiter(true, perOrigin, perDay);
    }

    private UsageLimiter limiter(boolean enabled, int perOrigin, int perDay) {
        LimitsProperties limits = new LimitsProperties(enabled, Duration.ofHours(1),
                perOrigin, perOrigin * 2, perDay, perDay * 2, 6000, 300);
        return new UsageLimiter(limits, clock);
    }

    @Test
    void refusesAnOriginAboveItsLimitAndSaysWhenToRetry() {
        UsageLimiter limiter = limiter(2, 100);
        limiter.acquire(UsageKind.EVALUATION, ALICE);
        clock.advance(Duration.ofMinutes(15));
        limiter.acquire(UsageKind.EVALUATION, ALICE);

        UsageLimitException refused = catchThrowableOfType(UsageLimitException.class,
                () -> limiter.acquire(UsageKind.EVALUATION, ALICE));

        assertThat(refused.scope()).isEqualTo(UsageLimitException.Scope.ORIGIN);
        assertThat(refused.kind()).isEqualTo(UsageKind.EVALUATION);
        // the window opened at START, so it reopens 45 minutes from now
        assertThat(refused.retryAfterSeconds()).isEqualTo(45 * 60);
    }

    @Test
    void originsAreCountedSeparately() {
        UsageLimiter limiter = limiter(1, 100);
        limiter.acquire(UsageKind.EVALUATION, ALICE);

        assertThatCode(() -> limiter.acquire(UsageKind.EVALUATION, BOB)).doesNotThrowAnyException();
    }

    @Test
    void kindsAreCountedSeparately() {
        UsageLimiter limiter = limiter(1, 100);
        limiter.acquire(UsageKind.EVALUATION, ALICE);

        assertThatCode(() -> limiter.acquire(UsageKind.TRANSCRIPTION, ALICE)).doesNotThrowAnyException();
    }

    @Test
    void aNewWindowStartsAfterTheWindowLength() {
        UsageLimiter limiter = limiter(1, 100);
        limiter.acquire(UsageKind.EVALUATION, ALICE);

        clock.advance(Duration.ofHours(1));

        assertThatCode(() -> limiter.acquire(UsageKind.EVALUATION, ALICE)).doesNotThrowAnyException();
    }

    @Test
    void refusesEveryoneAboveTheDailyCapUntilMidnightInBrasilia() {
        UsageLimiter limiter = limiter(10, 2);
        limiter.acquire(UsageKind.EVALUATION, ALICE);
        limiter.acquire(UsageKind.EVALUATION, BOB);

        UsageLimitException refused = catchThrowableOfType(UsageLimitException.class,
                () -> limiter.acquire(UsageKind.EVALUATION, "203.0.113.3"));

        assertThat(refused.scope()).isEqualTo(UsageLimitException.Scope.DAILY);
        // 10:00 in Brasília → 14 hours until midnight
        assertThat(refused.retryAfterSeconds()).isEqualTo(14 * 3600);
    }

    @Test
    void theDailyCapResetsAtMidnightInBrasiliaNotInUtc() {
        UsageLimiter limiter = limiter(10, 1);
        limiter.acquire(UsageKind.EVALUATION, ALICE);

        // 22:00 in Brasília is already the next day in UTC, but still the same day for the cap
        clock.set(Instant.parse("2026-11-09T01:00:00Z"));
        assertThatThrownBy(() -> limiter.acquire(UsageKind.EVALUATION, BOB))
                .isInstanceOf(UsageLimitException.class);

        clock.set(Instant.parse("2026-11-09T03:00:01Z")); // 00:00:01 in Brasília
        assertThatCode(() -> limiter.acquire(UsageKind.EVALUATION, BOB)).doesNotThrowAnyException();
    }

    @Test
    void aDailyRefusalLeavesTheOriginCountUntouchedInTheSameWindow() {
        UsageLimiter limiter = limiter(5, 1);
        limiter.acquire(UsageKind.EVALUATION, BOB);

        assertThatThrownBy(() -> limiter.acquire(UsageKind.EVALUATION, ALICE))
                .isInstanceOf(UsageLimitException.class);

        assertThat(limiter.usedInWindow(UsageKind.EVALUATION, ALICE)).isZero();
    }

    @Test
    void disabledLimitsNeverRefuse() {
        UsageLimiter limiter = limiter(false, 1, 1);

        for (int i = 0; i < 50; i++) {
            limiter.acquire(UsageKind.EVALUATION, ALICE);
        }
        assertThat(limiter.usedInWindow(UsageKind.EVALUATION, ALICE)).isZero();
    }

    @Test
    void expiredWindowsAreDroppedWhenManyOriginsPiledUp() {
        UsageLimiter limiter = limiter(10, 100_000);
        for (int i = 0; i <= UsageLimiter.MAX_TRACKED_ORIGINS; i++) {
            limiter.acquire(UsageKind.EVALUATION, "10.0." + (i / 256) + "." + (i % 256));
        }
        assertThat(limiter.trackedOrigins(UsageKind.EVALUATION)).isGreaterThan(UsageLimiter.MAX_TRACKED_ORIGINS);

        clock.advance(Duration.ofHours(2));
        limiter.acquire(UsageKind.EVALUATION, ALICE);

        assertThat(limiter.trackedOrigins(UsageKind.EVALUATION)).isEqualTo(1);
    }

    /** A clock the test moves by hand, so windows expire without sleeping. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        void set(Instant instant) {
            now = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
