package br.com.breno.penssay.web;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Counts paid model calls per client origin (fixed window) and for the whole app per day, in memory.
 * Callers reserve a slot right before calling the model; a reserved slot is not given back if the call fails,
 * because a failed call may still have been billed. The app runs as a single instance, and a restart resets the
 * counters: the credit cap on the provider key stays as the last barrier (specs/004-launch-protection).
 */
@Component
public class UsageLimiter {

    // above this many tracked origins, expired windows are dropped so memory stays bounded
    static final int MAX_TRACKED_ORIGINS = 10_000;
    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");

    private final LimitsProperties limits;
    private final Clock clock;
    private final Map<UsageKind, Map<String, Window>> windows = new EnumMap<>(UsageKind.class);
    private final Map<UsageKind, Integer> usedToday = new EnumMap<>(UsageKind.class);
    private LocalDate today;

    public UsageLimiter(LimitsProperties limits, Clock clock) {
        this.limits = limits;
        this.clock = clock;
        for (UsageKind kind : UsageKind.values()) {
            windows.put(kind, new HashMap<>());
            usedToday.put(kind, 0);
        }
    }

    /**
     * Reserves one paid call for this origin, or throws {@link UsageLimitException}.
     * Both checks and both counts happen under one lock, so concurrent requests cannot slip past a cap together,
     * and a refusal by the daily cap does not use the origin's share. The lock is held for microseconds only.
     */
    public synchronized void acquire(UsageKind kind, String origin) {
        if (!limits.enabled()) {
            return;
        }
        Instant now = clock.instant();
        rollDay(now);

        Map<String, Window> byOrigin = windows.get(kind);
        if (byOrigin.size() > MAX_TRACKED_ORIGINS) {
            byOrigin.values().removeIf(w -> w.expired(now, limits.window()));
        }
        Window window = byOrigin.get(origin);
        if (window == null || window.expired(now, limits.window())) {
            window = new Window(now);
        }

        if (window.count >= limits.perOrigin(kind)) {
            long wait = Duration.between(now, window.start.plus(limits.window())).toSeconds();
            throw new UsageLimitException(kind, UsageLimitException.Scope.ORIGIN, Math.max(1, wait));
        }
        if (usedToday.get(kind) >= limits.perDay(kind)) {
            long wait = Duration.between(now, today.plusDays(1).atStartOfDay(BRASILIA).toInstant()).toSeconds();
            throw new UsageLimitException(kind, UsageLimitException.Scope.DAILY, Math.max(1, wait));
        }

        window.count++;
        byOrigin.put(origin, window);
        usedToday.merge(kind, 1, Integer::sum);
    }

    synchronized int usedInWindow(UsageKind kind, String origin) {
        Window window = windows.get(kind).get(origin);
        return window == null || window.expired(clock.instant(), limits.window()) ? 0 : window.count;
    }

    synchronized int trackedOrigins(UsageKind kind) {
        return windows.get(kind).size();
    }

    private void rollDay(Instant now) {
        LocalDate date = LocalDate.ofInstant(now, BRASILIA);
        if (!date.equals(today)) {
            today = date;
            usedToday.replaceAll((kind, used) -> 0);
        }
    }

    private static final class Window {
        private final Instant start;
        private int count;

        Window(Instant start) {
            this.start = start;
        }

        boolean expired(Instant now, Duration length) {
            return !now.isBefore(start.plus(length));
        }
    }
}
